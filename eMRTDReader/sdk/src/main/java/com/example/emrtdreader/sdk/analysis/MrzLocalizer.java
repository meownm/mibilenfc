package com.example.emrtdreader.sdk.analysis;

import com.example.emrtdreader.sdk.models.MrzBox;

public interface MrzLocalizer {
    MrzBox locate(FrameInput frame);
}
