package com.zoick.farmmarket.domain.produce;
import com.zoick.farmmarket.domain.auth.FarmUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/batches")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
//Admin only-> no public access, customer see listing via listing controller
public class HarvestBatchController {
    private final HarvestBatchService harvestBatchService;

    @GetMapping
    public ResponseEntity<List<HarvestBatchResponse>> getActiveBatches() {
        return ResponseEntity.ok(harvestBatchService.getActiveBatches());
    }
    @GetMapping("/{id}")
    public ResponseEntity<HarvestBatchResponse> getBatchById(@PathVariable UUID id) {
        return ResponseEntity.ok(harvestBatchService.getBatchById(id));
    }
    @GetMapping("/produce/{produceId}")
    public ResponseEntity<List<HarvestBatchResponse>> getBatchesByProduce(@PathVariable UUID produceId) {
        return ResponseEntity.ok(harvestBatchService.getBatchesByProduce(produceId));
    }
    @PostMapping
    public ResponseEntity<HarvestBatchResponse> createBatch(@Valid @RequestBody CreateHarvestBatchRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(harvestBatchService.createBatch(request));
    }
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<HarvestBatchResponse> cancelBatch(@AuthenticationPrincipal FarmUserDetails principal,
                                                            @PathVariable UUID id) {
        return ResponseEntity.ok(harvestBatchService.cancelBatch(id, principal.getUserId()));
    }
}
