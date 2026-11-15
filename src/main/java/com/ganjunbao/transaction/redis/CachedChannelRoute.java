package com.ganjunbao.transaction.redis;

import com.ganjunbao.transaction.model.ChannelStatus;

public record CachedChannelRoute(
        Long id,
        String channelCode,
        String channelName,
        ChannelStatus status
) {
}
