package com.example.emrtdreader.sdk.models;

import java.io.Serializable;
import java.util.EnumSet;

public class GateResult implements Serializable {
    public final boolean pass;
    public final GateMetrics metrics;
    public final EnumSet<GateRejectReason> reasons;

    public GateResult(boolean pass, GateMetrics metrics, EnumSet<GateRejectReason> reasons) {
        this.pass = pass;
        this.metrics = metrics;
        this.reasons = reasons;
    }
}
