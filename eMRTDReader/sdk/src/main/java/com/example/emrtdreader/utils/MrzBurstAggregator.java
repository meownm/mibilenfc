package com.example.emrtdreader.utils;

import com.example.emrtdreader.models.MrzFormat;
import com.example.emrtdreader.models.MrzResult;

import java.util.*;

public class MrzBurstAggregator {
    private final int minFrames;
    private final int maxFrames;
    private final List<MrzResult> buffer = new ArrayList<>();

    public MrzBurstAggregator() {
        this(3, 10);
    }

    public MrzBurstAggregator(int minFrames, int maxFrames) {
        this.minFrames = minFrames;
        this.maxFrames = maxFrames;
    }

    public synchronized void reset() {
        buffer.clear();
    }

    public synchronized MrzResult addAndMaybeAggregate(MrzResult r) {
        if (r == null) return null;
        buffer.add(r);
        if (buffer.size() > maxFrames) buffer.remove(0);

        if (buffer.size() < minFrames) return null;

        // Prefer the format with more valid frames
        MrzResult td3 = aggregateFormat(MrzFormat.TD3);
        MrzResult td1 = aggregateFormat(MrzFormat.TD1);

        MrzResult best = pickBest(td3, td1);
        return best != null && best.confidence >= 3 ? best : null;
    }

    private MrzResult pickBest(MrzResult a, MrzResult b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.confidence >= b.confidence ? a : b;
    }

    private MrzResult aggregateFormat(MrzFormat fmt) {
        List<MrzResult> list = new ArrayList<>();
        for (MrzResult r : buffer) if (r.format == fmt) list.add(r);
        if (list.size() < minFrames) return null;

        if (fmt == MrzFormat.TD3) {
            String l1 = voteLine(list, 1, 44);
            String l2 = voteLine(list, 2, 44);
            // checksum-guided repair pass
            String repairedL2 = MrzRepair.repairTd3Line2(l2);
            int score = MrzValidation.scoreTd3(l1, repairedL2);
            return new MrzResult(l1, repairedL2, null, MrzFormat.TD3, score);
        } else {
            String l1 = voteLine(list, 1, 30);
            String l2 = voteLine(list, 2, 30);
            String l3 = voteLine(list, 3, 30);
            String[] repaired = MrzRepair.repairTd1(l1, l2, l3);
            int score = MrzValidation.scoreTd1(repaired[0], repaired[1], repaired[2]);
            return new MrzResult(repaired[0], repaired[1], repaired[2], MrzFormat.TD1, score);
        }
    }

    private String voteLine(List<MrzResult> list, int which, int len) {
        char[] out = new char[len];
        Arrays.fill(out, '<');

        for (int i = 0; i < len; i++) {
            Map<Character, Integer> freq = new HashMap<>();
            for (MrzResult r : list) {
                char c;
                if (which == 1) c = r.line1.charAt(i);
                else if (which == 2) c = r.line2.charAt(i);
                else c = r.line3.charAt(i);
                freq.put(c, freq.getOrDefault(c, 0) + 1);
            }
            char best = '<';
            int bestN = -1;
            for (Map.Entry<Character,Integer> e : freq.entrySet()) {
                if (e.getValue() > bestN) {
                    bestN = e.getValue();
                    best = e.getKey();
                }
            }
            out[i] = best;
        }
        return new String(out);
    }
}
