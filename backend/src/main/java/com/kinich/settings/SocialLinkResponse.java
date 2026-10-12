package com.kinich.settings;

import java.util.UUID;

public record SocialLinkResponse(UUID id, SocialPlatform platform, String label, String url, boolean visible,
                                 int position, long version) {

    static SocialLinkResponse from(SocialLink link) {
        return new SocialLinkResponse(link.getId(), link.getPlatform(), link.getLabel(), link.getUrl(),
                link.isVisible(), link.getPosition(), link.getVersion());
    }
}
