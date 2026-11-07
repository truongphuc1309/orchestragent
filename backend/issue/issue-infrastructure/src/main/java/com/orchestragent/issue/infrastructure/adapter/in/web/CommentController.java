package com.orchestragent.issue.infrastructure.adapter.in.web;

import com.orchestragent.issue.application.CommentService;
import com.orchestragent.issue.infrastructure.adapter.in.web.IssueDtos.CommentRequest;
import com.orchestragent.issue.infrastructure.adapter.in.web.IssueDtos.CommentResponse;
import com.orchestragent.platform.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/issues/{issueId}/comments")
class CommentController {

    private final CommentService comments;

    CommentController(CommentService comments) {
        this.comments = comments;
    }

    @GetMapping
    List<CommentResponse> list(CurrentUser user, @PathVariable UUID workspaceId, @PathVariable UUID issueId) {
        return comments.list(user.id(), workspaceId, issueId).stream().map(CommentResponse::from).toList();
    }

    @PostMapping
    ResponseEntity<CommentResponse> add(
            CurrentUser user,
            @PathVariable UUID workspaceId,
            @PathVariable UUID issueId,
            @Valid @RequestBody CommentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CommentResponse.from(comments.add(user.id(), workspaceId, issueId, request.body())));
    }
}
