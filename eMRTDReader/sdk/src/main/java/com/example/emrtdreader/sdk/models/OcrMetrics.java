package com.example.emrtdreader.sdk.models;

import java.io.Serializable;

public class OcrMetrics implements Serializable {
    public final double brightness;   // 0..255
    public final double contrast;     // stddev of luma
    public final double sharpness;    // variance of Laplacian (proxy)

    public OcrMetrics(double brightness, double contrast, double sharpness) {
        this.brightness = brightness;
        this.contrast = contrast;
        this.sharpness = sharpness;
    }
}
