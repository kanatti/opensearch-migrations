package org.opensearch.migrations.replay;

import org.opensearch.migrations.replay.datatypes.ITrafficStreamKey;
import org.opensearch.migrations.replay.traffic.source.ISimpleTrafficCaptureSource;
import org.opensearch.migrations.replay.traffic.source.ITrafficStreamWithKey;
import org.opensearch.migrations.replay.traffic.source.InputStreamOfTraffic;
import org.opensearch.migrations.replay.traffic.source.TrafficStreamWithEmbeddedKey;
import org.opensearch.migrations.trafficcapture.protos.TrafficStream;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

public class V0_1TrafficCaptureSource implements ISimpleTrafficCaptureSource {

    private static class Progress {
        boolean lastWasRead;
        int requestCount;

        public void add(TrafficStream incoming) {
            var list = incoming.getSubStreamList();
            lastWasRead = list.size() == 0 ? lastWasRead :
                    Optional.of(list.get(list.size()-1)).map(tso->tso.hasRead() || tso.hasReadSegment()).get();
            requestCount += list.stream().filter(tso->tso.hasRead()||tso.hasReadSegment()).count();
        }
    }

    private final InputStreamOfTraffic trafficSource;
    private final HashMap<String, Progress> connectionProgressMap;

    public V0_1TrafficCaptureSource(String filename) throws IOException {
        var compressedIs = new FileInputStream(filename);
        var is = new GZIPInputStream(compressedIs);
        trafficSource = new InputStreamOfTraffic(is);
        connectionProgressMap = new HashMap<>();
    }

    @Override
    public void commitTrafficStream(ITrafficStreamKey trafficStreamKey) {
        // do nothing
    }

    @Override
    public CompletableFuture<List<ITrafficStreamWithKey>> readNextTrafficStreamChunk() {
        return trafficSource.readNextTrafficStreamChunk()
                .thenApply(ltswk->
                        ltswk.stream().map(this::upgradeTrafficStream).collect(Collectors.toList()));
    }

    private ITrafficStreamWithKey upgradeTrafficStream(ITrafficStreamWithKey streamWithKey) {
        var incoming = streamWithKey.getStream();
        var outgoingBuilder = TrafficStream.newBuilder()
                .setNodeId(incoming.getNodeId())
                .setConnectionId(incoming.getConnectionId());
        if (incoming.hasNumber()) {
            outgoingBuilder.setNumber(incoming.getNumber());
        } else {
            outgoingBuilder.setNumberOfThisLastChunk(incoming.getNumberOfThisLastChunk());
        }
        var progress = connectionProgressMap.get(incoming.getConnectionId());
        if (progress == null) {
            progress = new Progress();
            progress.lastWasRead = streamWithKey.getKey().getTrafficStreamIndex() != 0;
            connectionProgressMap.put(incoming.getConnectionId(), progress);
        }
        outgoingBuilder.setLastObservationWasUnterminatedRead(progress.lastWasRead);
        outgoingBuilder.setRequestCount(progress.requestCount);
        outgoingBuilder.addAllSubStream(incoming.getSubStreamList());
        progress.add(incoming);
        if (incoming.hasNumberOfThisLastChunk()) {
            connectionProgressMap.remove(incoming.getConnectionId());
        }
        return new TrafficStreamWithEmbeddedKey(outgoingBuilder.build());
    }

    @Override
    public void close() {
        // do nothing
    }
}