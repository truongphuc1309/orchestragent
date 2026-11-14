package com.orchestragent.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orchestragent.identity.application.Fakes.InMemoryAvatarStorage;
import com.orchestragent.identity.application.Fakes.InMemoryUsers;
import com.orchestragent.identity.domain.Avatar;
import com.orchestragent.identity.domain.User;
import com.orchestragent.shared.error.NotFoundException;
import com.orchestragent.shared.error.UnauthorizedException;
import com.orchestragent.shared.error.ValidationException;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProfileServiceTest {

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 1, 2, 3};
    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 4, 5, 6};

    private final InMemoryUsers users = new InMemoryUsers();
    private final InMemoryAvatarStorage storage = new InMemoryAvatarStorage();
    private final ProfileService service = new ProfileService(users, storage);
    private final User ada = users.add("ada@example.com");

    @Test
    void newUserHasEmptyBioAndNoAvatar() {
        ProfileView me = new GetMe(users).execute(ada.id().value());

        assertThat(me.name()).isEqualTo("ada");
        assertThat(me.bio()).isEmpty();
        assertThat(me.avatarUrl()).isNull();
    }

    @Test
    void updateSetsTrimmedNameAndBio() {
        ProfileView me = service.update(ada.id().value(), "  Ada Lovelace ", " First programmer\r\nMathematician ");

        assertThat(me.name()).isEqualTo("Ada Lovelace");
        assertThat(me.bio()).isEqualTo("First programmer\nMathematician");
        assertThat(me.email()).isEqualTo("ada@example.com");
    }

    @Test
    void updateKeepsTheAvatar() {
        String url = service.changeAvatar(ada.id().value(), PNG).avatarUrl();

        assertThat(service.update(ada.id().value(), "Ada", "").avatarUrl()).isEqualTo(url);
    }

    @Test
    void invalidBioChangesNothing() {
        assertThatThrownBy(() -> service.update(ada.id().value(), "Ada Lovelace", "x".repeat(501)))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("code", "profile.bio_too_long");

        assertThat(users.findById(ada.id()).orElseThrow().name()).isEqualTo("ada");
    }

    @Test
    void unknownUserIsNotAuthenticated() {
        assertThatThrownBy(() -> service.update(UUID.randomUUID(), "Ada", ""))
                .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> service.changeAvatar(UUID.randomUUID(), PNG))
                .isInstanceOf(UnauthorizedException.class);
        assertThat(storage.byAvatarId).isEmpty();
    }

    @Test
    void changeAvatarStoresThePictureAndServesIt() {
        ProfileView me = service.changeAvatar(ada.id().value(), PNG);

        Avatar avatar = users.findById(ada.id()).orElseThrow().avatar().orElseThrow();
        assertThat(me.avatarUrl()).isEqualTo("/api/users/" + ada.id().value() + "/avatar/" + avatar.id());
        ProfileService.AvatarPicture picture = service.avatar(ada.id().value(), avatar.id());
        assertThat(picture.content()).isEqualTo(PNG);
        assertThat(picture.mediaType()).isEqualTo("image/png");
    }

    @Test
    void replacingTheAvatarDeletesTheOldPicture() {
        service.changeAvatar(ada.id().value(), PNG);
        UUID first = currentAvatarId();

        service.changeAvatar(ada.id().value(), JPEG);
        UUID second = currentAvatarId();

        assertThat(second).isNotEqualTo(first);
        assertThat(storage.discarded).containsExactly(first);
        assertThat(storage.byAvatarId).containsOnlyKeys(second);
        assertThat(service.avatar(ada.id().value(), second).mediaType()).isEqualTo("image/jpeg");
    }

    @Test
    void oldAvatarAddressIsNotFound() {
        service.changeAvatar(ada.id().value(), PNG);
        UUID first = currentAvatarId();
        service.changeAvatar(ada.id().value(), JPEG);

        assertThatThrownBy(() -> service.avatar(ada.id().value(), first)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.avatar(ada.id().value(), UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void unsupportedFileIsRefusedBeforeAnythingIsStored() {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes();

        assertThatThrownBy(() -> service.changeAvatar(ada.id().value(), svg))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("code", "avatar.unsupported_type");
        assertThat(storage.byAvatarId).isEmpty();
        assertThat(users.findById(ada.id()).orElseThrow().avatar()).isEmpty();
    }

    @Test
    void tooLargeFileIsRefused() {
        byte[] big = Arrays.copyOf(PNG, Avatar.MAX_BYTES + 1);

        assertThatThrownBy(() -> service.changeAvatar(ada.id().value(), big))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("code", "avatar.too_large");
        assertThat(storage.byAvatarId).isEmpty();
    }

    @Test
    void storageFailureLeavesTheOldAvatar() {
        service.changeAvatar(ada.id().value(), PNG);
        UUID first = currentAvatarId();
        storage.down = true;

        assertThatThrownBy(() -> service.changeAvatar(ada.id().value(), JPEG)).isInstanceOf(IllegalStateException.class);

        assertThat(currentAvatarId()).isEqualTo(first);
        assertThat(storage.byAvatarId).containsOnlyKeys(first);
    }

    @Test
    void databaseFailureDeletesTheNewPictureAndKeepsTheOld() {
        service.changeAvatar(ada.id().value(), PNG);
        UUID first = currentAvatarId();
        users.failNextAvatarWrite = true;

        assertThatThrownBy(() -> service.changeAvatar(ada.id().value(), JPEG)).isInstanceOf(IllegalStateException.class);

        assertThat(currentAvatarId()).isEqualTo(first);
        assertThat(storage.byAvatarId).containsOnlyKeys(first);
        assertThat(storage.discarded).hasSize(1).doesNotContain(first);
    }

    @Test
    void removeAvatarClearsItAndDeletesThePicture() {
        service.changeAvatar(ada.id().value(), PNG);
        UUID first = currentAvatarId();

        ProfileView me = service.removeAvatar(ada.id().value());

        assertThat(me.avatarUrl()).isNull();
        assertThat(storage.discarded).containsExactly(first);
        assertThat(storage.byAvatarId).isEmpty();
        assertThatThrownBy(() -> service.avatar(ada.id().value(), first)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void removingWithoutAvatarDoesNothing() {
        assertThat(service.removeAvatar(ada.id().value()).avatarUrl()).isNull();
        assertThat(storage.discarded).isEmpty();
    }

    @Test
    void directoryViewCarriesTheAvatarUrl() {
        String url = service.changeAvatar(ada.id().value(), PNG).avatarUrl();

        assertThat(new UserDirectoryService(users).findById(ada.id().value()).orElseThrow().avatarUrl()).isEqualTo(url);
    }

    private UUID currentAvatarId() {
        return users.findById(ada.id()).orElseThrow().avatar().orElseThrow().id();
    }
}
