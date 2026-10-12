package com.kinich.media;

import com.kinich.auth.AuthenticatedUser;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Solo HTTP ↔ DTO. El nombre y el Content-Type que envía el cliente se ignoran por completo. Solo se sirven
 * representaciones web: no existe endpoint del original.
 */
@RestController
@RequestMapping("/api/admin/media")
@RequiredArgsConstructor
class AdminMediaController {

    private final MediaService media;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    MediaResponse upload(@RequestParam("file") MultipartFile file, @AuthenticationPrincipal AuthenticatedUser user)
            throws IOException {
        MediaAsset saved = media.upload(file.getBytes(), user.id());
        return MediaResponse.from(saved, renditionsOf(List.of(saved)).get(saved.getId()));
    }

    @GetMapping
    List<MediaResponse> list() {
        List<MediaAsset> assets = media.list();
        Map<UUID, MediaRenditions> renditions = renditionsOf(assets);
        return assets.stream().map(asset -> MediaResponse.from(asset, renditions.get(asset.getId()))).toList();
    }

    @GetMapping("/files/{fileId}")
    ResponseEntity<?> file(@PathVariable UUID fileId,
                           @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
        return MediaHttp.image(media.openWebFileForAdmin(fileId), ifNoneMatch);
    }

    private Map<UUID, MediaRenditions> renditionsOf(List<MediaAsset> assets) {
        return media.renditions(assets.stream().map(MediaAsset::getId).toList(), MediaAudience.ADMIN);
    }
}
