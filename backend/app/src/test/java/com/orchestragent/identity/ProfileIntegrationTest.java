package com.orchestragent.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.orchestragent.platform.storage.ObjectStorage;
import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/** Name, bio and avatar of the signed-in user, with the avatar stored in a real S3-compatible store. */
class ProfileIntegrationTest extends IdentityIntegrationTest {

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13, 'I', 'H', 'D', 'R'};
    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F'};

    @Autowired
    ObjectStorage storage;

    private MockMultipartHttpServletRequestBuilder upload(byte[] content, String declaredType) {
        MockMultipartHttpServletRequestBuilder builder = multipart(HttpMethod.PUT, "/api/me/avatar");
        builder.file(new MockMultipartFile("file", "picture.png", declaredType, content));
        return builder;
    }

    private String uploadAvatar(Cookie session, byte[] content) throws Exception {
        String body = mvc.perform(upload(content, "image/png").cookie(session).with(xsrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.avatarUrl");
    }

    private String userId(Cookie session) throws Exception {
        return JsonPath.read(mvc.perform(get("/api/me").cookie(session)).andReturn().getResponse().getContentAsString(), "$.id");
    }

    /** {@code /api/users/{userId}/avatar/{avatarId}} is stored as {@code identity/avatars/{userId}/{avatarId}}. */
    private static String objectKey(String avatarUrl) {
        return "identity/avatars/" + avatarUrl.substring("/api/users/".length()).replace("/avatar/", "/");
    }

    @Test
    void newUserHasEmptyBioAndNoAvatar() throws Exception {
        String email = uniqueEmail();
        fixCode();
        requestCode(email);
        Cookie session = verifyCode(email, CODE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bio").value(""))
                .andExpect(jsonPath("$.avatarUrl").value(Matchers.nullValue()))
                .andReturn().getResponse().getCookie("oa_session");

        mvc.perform(get("/api/me").cookie(session))
                .andExpect(jsonPath("$.name").value(email.substring(0, email.indexOf('@'))))
                .andExpect(jsonPath("$.bio").value(""))
                .andExpect(jsonPath("$.avatarUrl").value(Matchers.nullValue()));
    }

    @Test
    void updateChangesNameAndBioButNotEmail() throws Exception {
        String email = uniqueEmail();
        Cookie session = login(email);

        mvc.perform(json(patch("/api/me"),
                        "{\"name\":\"  Ada Lovelace \",\"bio\":\"Line one\\r\\nLine two\",\"email\":\"evil@example.com\"}")
                        .cookie(session).with(xsrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ada Lovelace"))
                .andExpect(jsonPath("$.bio").value("Line one\nLine two"))
                .andExpect(jsonPath("$.email").value(email));

        mvc.perform(get("/api/me").cookie(session))
                .andExpect(jsonPath("$.name").value("Ada Lovelace"))
                .andExpect(jsonPath("$.bio").value("Line one\nLine two"));
    }

    @Test
    void updateNeedsCsrfToken() throws Exception {
        Cookie session = login(uniqueEmail());

        mvc.perform(json(patch("/api/me"), "{\"name\":\"Ada\",\"bio\":\"\"}").cookie(session))
                .andExpect(status().isForbidden());
        mvc.perform(upload(PNG, "image/png").cookie(session)).andExpect(status().isForbidden());
    }

    @Test
    void invalidValuesAreRefusedWithTheirCode() throws Exception {
        Cookie session = login(uniqueEmail());

        mvc.perform(json(patch("/api/me"), "{\"name\":\"   \",\"bio\":\"\"}").cookie(session).with(xsrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("profile.name_required"));
        mvc.perform(json(patch("/api/me"), "{\"name\":\"Ada\",\"bio\":\"" + "b".repeat(501) + "\"}")
                        .cookie(session).with(xsrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("profile.bio_too_long"));
        mvc.perform(json(patch("/api/me"), "{\"bio\":\"no name\"}").cookie(session).with(xsrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadedAvatarIsServedWithSafeHeaders() throws Exception {
        Cookie session = login(uniqueEmail());
        String url = uploadAvatar(session, PNG);

        assertThat(url).startsWith("/api/users/" + userId(session) + "/avatar/");
        mvc.perform(get("/api/me").cookie(session)).andExpect(jsonPath("$.avatarUrl").value(url));

        Cookie someoneElse = login(uniqueEmail());
        mvc.perform(get(url).cookie(someoneElse))
                .andExpect(status().isOk())
                .andExpect(content().bytes(PNG))
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().stringValues("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", Matchers.containsString("immutable")))
                .andExpect(header().string("Cache-Control", Matchers.containsString("private")))
                .andExpect(header().string("Content-Disposition", "inline"));
    }

    @Test
    void avatarNeedsASignedInUser() throws Exception {
        Cookie session = login(uniqueEmail());
        String url = uploadAvatar(session, PNG);

        mvc.perform(get(url)).andExpect(status().isUnauthorized());
    }

    @Test
    void theFormatComesFromTheContentNotFromTheClient() throws Exception {
        Cookie session = login(uniqueEmail());

        String url = JsonPath.read(mvc.perform(upload(JPEG, "image/png").cookie(session).with(xsrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.avatarUrl");
        mvc.perform(get(url).cookie(session)).andExpect(header().string("Content-Type", "image/jpeg"));

        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes();
        mvc.perform(upload(svg, "image/png").cookie(session).with(xsrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("avatar.unsupported_type"));
        mvc.perform(get("/api/me").cookie(session)).andExpect(jsonPath("$.avatarUrl").value(url));
    }

    /**
     * MockMvc skips the servlet's multipart size limit (which answers 413 in a real server, see
     * {@code GlobalExceptionHandlerTest}), so this reaches the domain's own 2 MB check.
     */
    @Test
    void tooLargePictureIsRefused() throws Exception {
        Cookie session = login(uniqueEmail());

        mvc.perform(upload(Arrays.copyOf(PNG, 2 * 1024 * 1024 + 1), "image/png").cookie(session).with(xsrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("avatar.too_large"));
    }

    @Test
    void replacingTheAvatarDeletesTheOldPicture() throws Exception {
        Cookie session = login(uniqueEmail());
        String first = uploadAvatar(session, PNG);
        assertThat(storage.get(objectKey(first))).isPresent();

        String second = uploadAvatar(session, JPEG);

        assertThat(second).isNotEqualTo(first);
        mvc.perform(get(first).cookie(session)).andExpect(status().isNotFound());
        mvc.perform(get(second).cookie(session)).andExpect(status().isOk()).andExpect(content().bytes(JPEG));
        assertThat(storage.get(objectKey(first))).isEmpty();
    }

    @Test
    void removingTheAvatarDeletesThePicture() throws Exception {
        Cookie session = login(uniqueEmail());
        String url = uploadAvatar(session, PNG);

        mvc.perform(delete("/api/me/avatar").cookie(session).with(xsrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").value(Matchers.nullValue()));

        mvc.perform(get(url).cookie(session)).andExpect(status().isNotFound());
        assertThat(storage.get(objectKey(url))).isEmpty();
    }

    @Test
    void updatingTheNameKeepsTheAvatar() throws Exception {
        Cookie session = login(uniqueEmail());
        String url = uploadAvatar(session, PNG);

        mvc.perform(json(patch("/api/me"), "{\"name\":\"Ada\",\"bio\":\"\"}").cookie(session).with(xsrf()))
                .andExpect(jsonPath("$.avatarUrl").value(url));
    }

    @Test
    void anApiTokenCannotEditTheProfile() throws Exception {
        Cookie session = login(uniqueEmail());
        String created = mvc.perform(json(post("/api/me/tokens"), "{\"name\":\"laptop\"}").cookie(session).with(xsrf()))
                .andReturn().getResponse().getContentAsString();
        String secret = JsonPath.read(created, "$.secret");

        mvc.perform(json(patch("/api/me"), "{\"name\":\"Hacked\",\"bio\":\"\"}")
                        .header("Authorization", "Bearer " + secret).with(xsrf()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").cookie(session)).andExpect(jsonPath("$.name").value(Matchers.not("Hacked")));
    }

    @Test
    void theAvatarShowsWhereverThePersonAppears() throws Exception {
        Cookie session = login(uniqueEmail());
        String me = userId(session);
        String url = uploadAvatar(session, PNG);
        String workspaceId = JsonPath.read(mvc.perform(json(post("/api/workspaces"), "{\"name\":\"Avatars\"}")
                        .cookie(session).with(xsrf()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");

        mvc.perform(get("/api/workspaces/" + workspaceId + "/members").cookie(session))
                .andExpect(jsonPath("$[0].userId").value(me))
                .andExpect(jsonPath("$[0].avatarUrl").value(url));

        String issues = "/api/workspaces/" + workspaceId + "/issues";
        String issue = mvc.perform(json(post(issues), "{\"projectId\":\"" + projectIn(workspaceId, session)
                                + "\",\"title\":\"Hello\",\"assignee\":{\"type\":\"member\",\"id\":\"" + me + "\"}}")
                        .cookie(session).with(xsrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.creator.avatarUrl").value(url))
                .andExpect(jsonPath("$.assignee.avatarUrl").value(url))
                .andReturn().getResponse().getContentAsString();
        String issueId = JsonPath.read(issue, "$.id");

        mvc.perform(json(post(issues + "/" + issueId + "/comments"), "{\"body\":\"hi\"}").cookie(session).with(xsrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.author.avatarUrl").value(url));
        mvc.perform(get(issues).cookie(session))
                .andExpect(jsonPath("$.items[0].assignee.avatarUrl").value(url));
    }
}
