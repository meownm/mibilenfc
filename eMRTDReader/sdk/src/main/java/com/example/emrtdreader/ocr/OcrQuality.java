package com.example.emrtdreader.ocr;

import android.graphics.Bitmap;

import com.example.emrtdreader.models.OcrMetrics;

public final class OcrQuality {
    private OcrQuality() {}

    public static OcrMetrics compute(Bitmap bmp) {
        if (bmp == null) return new OcrMetrics(0,0,0);
        final int w = bmp.getWidth();
        final int h = bmp.getHeight();
        int[] pixels = new int[w*h];
        bmp.getPixels(pixels, 0, w, 0, 0, w, h);

        // compute luma
        double sum = 0;
        double sum2 = 0;

        // For sharpness: variance of laplacian (simple 3x3 on luma)
        double lapSum = 0;
        double lapSum2 = 0;
        int lapCount = 0;

        double[] luma = new double[pixels.length];
        for (int i=0;i<pixels.length;i++){
            int p = pixels[i];
            int r = (p>>16)&0xFF;
            int g = (p>>8)&0xFF;
            int b = (p)&0xFF;
            double y = 0.299*r + 0.587*g + 0.114*b;
            luma[i]=y;
            sum += y;
            sum2 += y*y;
        }
        double mean = sum / luma.length;
        double var = (sum2 / luma.length) - mean*mean;
        double std = Math.sqrt(Math.max(0,var));

        // Laplacian
        for (int y=1;y<h-1;y++){
            for (int x=1;x<w-1;x++){
                int idx = y*w+x;
                double c = luma[idx];
                double lap = (-4*c
                        + luma[idx-1]
                        + luma[idx+1]
                        + luma[idx-w]
                        + luma[idx+w]);
                lapSum += lap;
                lapSum2 += lap*lap;
                lapCount++;
            }
        }
        double lapMean = lapCount>0 ? lapSum/lapCount : 0;
        double lapVar = lapCount>0 ? (lapSum2/lapCount - lapMean*lapMean) : 0;

        return new OcrMetrics(mean, std, Math.max(0, lapVar));
    }
}
