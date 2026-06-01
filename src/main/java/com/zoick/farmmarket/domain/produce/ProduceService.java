package com.zoick.farmmarket.domain.produce;
import com.zoick.farmmarket.infrastructure.CloudinaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
//produce is a template, it holds no stock just business rule as you cannot create produce for non-existent category
public class ProduceService {
    private final ProduceRepository produceRepository;
    private final ProduceCategoryRepository produceCategoryRepository;
    private final HarvestBatchRepository harvestBatchRepository;
    private final CloudinaryService cloudinaryService;

    public List<ProduceResponse> getAllActiveProduce(){
        return produceRepository.findAllActiveWithCategory().stream().map(ProduceResponse::from).collect(Collectors.toList());
    }
    public List<ProduceResponse> getProduceByCategory(UUID categoryId){
        return produceRepository.findAllByCategoryIdActiveWithCategory(categoryId).stream().map(ProduceResponse::from)
                .collect(Collectors.toList());
    }
    public ProduceResponse getProduceById(UUID id){
        return ProduceResponse.from(produceRepository.findByIdWithCategory(id).orElseThrow(()-> new IllegalArgumentException("Produce not found: "+ id)));
    }
    @Transactional
    public ProduceResponse createProduce(CreateProduceRequest request){
        ProduceCategory category= produceCategoryRepository.findById(request.getCategoryId()).orElseThrow(() -> new IllegalArgumentException(
                "Category not found: "+ request.getCategoryId()));
        Produce produce = new Produce();
        produce.setCategory(category);
        produce.setName(request.getName());
        produce.setDescription(request.getDescription());
        produce.setUnit(request.getUnit());
        produce.setImageUrl(request.getImageUrl());
        produce.setActive(true);
        return ProduceResponse.from(produceRepository.save(produce));
    }
    public ProduceResponse uploadImage(UUID produceId, MultipartFile file){
        String url= cloudinaryService.uploadImage(file, "barnCart/produce");
        Produce produce= produceRepository.findByIdWithCategory(produceId).orElseThrow(() -> new IllegalArgumentException(
                "Produce not found: "+ produceId));
        produce.setImageUrl(url);
        return ProduceResponse.from(produceRepository.save(produce));
    }
    @Transactional
    public ProduceResponse deactivateProduce(UUID id){
        //block deactivation if baches exist
        if(harvestBatchRepository.existsByProduceIdAndStatus(id, BatchStatus.ACTIVE)){
            throw new IllegalArgumentException("Cannot deactivate produce with active batches. "+
                    "Cancel all active batches first.");
        }
        Produce produce= produceRepository.findByIdWithCategory(id).orElseThrow(() -> new IllegalArgumentException("Produce not found: "+ id));
        produce.setActive(false);
        return ProduceResponse.from(produceRepository.save(produce));
    }
}
