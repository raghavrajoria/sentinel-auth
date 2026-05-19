package com.sentinel.audit.controller;

import com.sentinel.audit.service.DriftDetectionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/v1/drift")
public class DriftController {

    private final DriftDetectionService driftDetectionService;

    public DriftController(DriftDetectionService driftDetectionService) {
        this.driftDetectionService = driftDetectionService;
    }

    // GET /v1/drift/report — latest drift analysis
    @GetMapping("/report")
    public ResponseEntity<Map<String, Object>> report() {
        return ResponseEntity.ok(driftDetectionService.getLatestReport());
    }

    // POST /v1/drift/run — trigger immediately (for testing)
    @PostMapping("/run")
    public ResponseEntity<Map<String, Object>> run() {
        driftDetectionService.runNow();
        return ResponseEntity.ok(driftDetectionService.getLatestReport());
    }
}