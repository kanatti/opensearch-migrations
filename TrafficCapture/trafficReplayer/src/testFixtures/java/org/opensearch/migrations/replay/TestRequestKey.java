package org.opensearch.migrations.replay;

import org.opensearch.migrations.tracing.SimpleMeteringClosure;
import org.opensearch.migrations.replay.datatypes.PojoTrafficStreamKey;
import org.opensearch.migrations.replay.datatypes.UniqueReplayerRequestKey;
import org.opensearch.migrations.replay.tracing.ChannelKeyContext;
import org.opensearch.migrations.replay.tracing.RequestContext;

public class TestRequestKey {

    private TestRequestKey() {}

    public static final RequestContext getTestConnectionRequestContext(int replayerIdx) {
        var rk = new UniqueReplayerRequestKey(
                new PojoTrafficStreamKey("testNodeId", "testConnectionId", 0),
                0, replayerIdx);
        var smc = new SimpleMeteringClosure("test");
        var channelKeyContext = new ChannelKeyContext(rk.trafficStreamKey, smc.makeSpanContinuation("test", null));
        return new RequestContext(channelKeyContext, rk, smc.makeSpanContinuation("test2"));
    }
}
