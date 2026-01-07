package com.example.emrtdreader.sdk.analysis;

import com.example.emrtdreader.sdk.models.MrzParseResult;
import com.example.emrtdreader.sdk.models.MrzResult;
import com.example.emrtdreader.sdk.models.NormalizedMrz;
import com.example.emrtdreader.sdk.models.OcrOutput;
import com.example.emrtdreader.sdk.ocr.MrzTextProcessor;
import com.example.emrtdreader.sdk.utils.MrzParserValidator;

import java.util.ArrayList;
import java.util.List;

public final class DefaultMrzPipelineParser implements MrzPipelineParser {
    @Override
    public MrzParseResult parse(OcrOutput output) {
        if (output == null || output.rawText == null) {
            return null;
        }
        MrzResult normalized = MrzTextProcessor.normalizeAndRepair(output.rawText);
        if (normalized == null) {
            return null;
        }
        List<String> lines = new ArrayList<>();
        lines.add(normalized.line1);
        lines.add(normalized.line2);
        if (normalized.line3 != null) {
            lines.add(normalized.line3);
        }
        return MrzParserValidator.parse(new NormalizedMrz(lines));
    }
}
