package com.zoick.farmmarket.domain.produce;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ListingService {
    private final ListingRepository listingRepository;
    private final HarvestBatchRepository harvestBatchRepository;

    @Transactional(readOnly = true)
    public Page<ListingResponse> getAllActiveListings(int page, int size){
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return listingRepository.findAllActiveWithDetails(pageable).map(ListingResponse::from);
    }
    @Transactional(readOnly = true)
    public ListingResponse getListingById(UUID id){
        Listing listing = listingRepository.findByIdWithDetails(id).orElseThrow(() -> new IllegalArgumentException("Listing not found: "+ id));
        return ListingResponse.from(listing);
    }
    @Transactional(readOnly = true)
    public ListingResponse getListingByBatchId(UUID batchId){
        Listing listing = listingRepository.findByBatchId(batchId).orElseThrow(() -> new IllegalArgumentException("No listing found for batch: "+ batchId));
        return ListingResponse.from(listing);
    }
    @Transactional
    public ListingResponse createListing(CreateListingRequest request){
        if(request.getBulkPrice().compareTo(request.getRetailPrice()) >= 0){
            throw new IllegalArgumentException("Bulk price must be less than retail price");
        }
        //one listing per batch
        if(listingRepository.existsByBatchId(request.getBatchId())){
            throw new IllegalArgumentException("Listing already exists for batch: "+ request.getBatchId());
        }
        HarvestBatch batch =  harvestBatchRepository.findById(request.getBatchId()).orElseThrow(() -> new IllegalArgumentException("Batch not found: " + request.getBatchId()));
        if(batch.getStatus() != BatchStatus.ACTIVE){
            throw new IllegalArgumentException("Cannot create listing for a non-ACTIVE batch");
        }
        //cross-field validation(bagWeightKg required when produce unit is Bag)
        ProduceUnit unit = batch.getProduce().getUnit();
        if(unit== ProduceUnit.BAG && request.getBagWeightKg()== null){
            throw new IllegalArgumentException("bagWeightKg is required when produce is Bag");
        }
        if(unit != ProduceUnit.BAG && request.getBagWeightKg() != null){
            throw new IllegalArgumentException("bagWeightKg must not be set when produce unit is not Bag");
        }
        Listing listing= new Listing();
        listing.setBatch(batch);
        listing.setRetailPrice(request.getRetailPrice());
        listing.setBulkPrice(request.getBulkPrice());
        listing.setMinBulkQuantity(request.getMinBulkQuantity());
        listing.setCurrency(request.getCurrency());
        listing.setLowStockThresholdPct(request.getLowStockThresholdPct());
        listing.setActive(true);
        return ListingResponse.from(listingRepository.save(listing));
    }
    @Transactional
    public ListingResponse deactivateListing(UUID id){
        Listing listing= listingRepository.findByIdWithDetails(id).orElseThrow(() -> new IllegalArgumentException("Listing not found: "+ id));
        listing.setActive(false);
        return ListingResponse.from(listingRepository.save(listing));
    }
}
