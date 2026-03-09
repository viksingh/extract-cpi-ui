package com.sakiv.cpi.extractor.service;

/**
 * Callback interface for reporting extraction progress from CpiApiService.
 */
public interface ExtractionProgressCallback {

    /**
     * Called to report progress during extraction.
     *
     * @param phase   Human-readable description of the current phase
     * @param progress Progress value between 0.0 and 1.0
     */
    void onProgress(String phase, double progress);
}
