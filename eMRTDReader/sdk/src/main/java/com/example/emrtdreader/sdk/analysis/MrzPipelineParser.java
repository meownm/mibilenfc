package com.example.emrtdreader.sdk.analysis;

import com.example.emrtdreader.sdk.models.MrzParseResult;
import com.example.emrtdreader.sdk.models.OcrOutput;

public interface MrzPipelineParser {
    MrzParseResult parse(OcrOutput output);
}
