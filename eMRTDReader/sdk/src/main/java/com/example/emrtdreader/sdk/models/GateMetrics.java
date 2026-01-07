package com.example.emrtdreader.sdk.models;

import java.io.Serializable;

public class GateMetrics implements Serializable {
    public final float brightnessMean;
    public final float contrastStd;
    public final float blurVarLap;
    public final float motionMad;

    public GateMetrics(float brightnessMean, float contrastStd, float blurVarLap, float motionMad) {
        this.brightnessMean = brightnessMean;
        this.contrastStd = contrastStd;
        this.blurVarLap = blurVarLap;
        this.motionMad = motionMad;
    }
}
