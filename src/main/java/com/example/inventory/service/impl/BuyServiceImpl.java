package com.example.inventory.service.impl;

import com.example.inventory.dto.BuyDetailDto;
import com.example.inventory.model.Buy;
import com.example.inventory.dto.BuyDto;
import com.example.inventory.model.BuyProduct;
import com.example.inventory.model.Product;
import com.example.inventory.repository.BuyRepository;
import com.example.inventory.repository.ProductRepository;
import com.example.inventory.service.BuyService;
import io.micrometer.common.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BuyServiceImpl implements BuyService {

    private final BuyRepository buyRepository;
    private final ProductRepository productRepository;

    @Override
    public ResponseEntity<List<BuyDto>> getBuys() {
        log.info("Init getBuys");
        ResponseEntity<List<BuyDto>> response;
        try {
            List<Buy> buyList = buyRepository.findAll();
            List<BuyDto> buyDtoList = buyList.stream().map(buy -> BuyDto.builder()
                    .date(buy.getDate())
                    .clientIdType(buy.getClientIdType())
                    .clientId(buy.getClientId())
                    .clientName(buy.getClientName())
                    .products(buy.getProducts().stream().map(buyProduct -> BuyDetailDto.builder()
                            .productId(buyProduct.getProduct().getProductId())
                            .name(buyProduct.getProduct().getName())
                            .quantity(buyProduct.getQuantity()).build())
                            .toList()).build())
                    .toList();
            response = ResponseEntity.ok(buyDtoList);
        } catch (Exception e) {
            log.info("Error in Init with messageError: {}", e.getMessage());
            response = ResponseEntity.internalServerError().build();
        }
        return response;
    }

    @Override
    public ResponseEntity<String> saveBuy(BuyDto buyDto) {
        log.info("Init saveBuy with: {}", buyDto);
        ResponseEntity<String> response;
        try {
            String responseIsAValidPurchase = isAValidPurchase(buyDto.getProducts());
            if (StringUtils.isBlank(responseIsAValidPurchase)) {
                Buy buy = Buy.builder()
                        .date(buyDto.getDate())
                        .clientIdType(buyDto.getClientIdType())
                        .clientId(buyDto.getClientId())
                        .clientName(buyDto.getClientName())
                        .products(buyDto.getProducts()
                                .stream()
                                .map(buyDetailDto -> BuyProduct.builder()
                                        .product(Product.builder()
                                                .productId(buyDetailDto.getProductId()).build())
                                        .quantity(buyDetailDto.getQuantity()).build()).collect(Collectors.toSet())).build();

                buy.getProducts().forEach(buyProduct -> buyProduct.setBuy(buy));
                buyRepository.save(buy);
                deductUnits(buyDto.getProducts());
                response = ResponseEntity.ok().build();
            }else {
                response = ResponseEntity.badRequest().body(responseIsAValidPurchase);
            }
        } catch (Exception e){
            log.info("Error in saveBuy with messageError: {}", e.getMessage());
            response = ResponseEntity.internalServerError().build();
        }
        return response;
    }

    /**
     * Method to validate buy restrictions
     *
     * @param buyDetailDtoList //productList
     * @return //errorMessage in case of a restriction
     */
    private String isAValidPurchase(List<BuyDetailDto> buyDetailDtoList) {
        //Validate is it's available
        String response = "";
        for (BuyDetailDto buyDetailDto : buyDetailDtoList) {
            Optional<Product> productOptional = productRepository.findById(buyDetailDto.getProductId());
            if(productOptional.isPresent()){
                Product product = productOptional.get();
                //validate availability
                if (!product.isEnabled()) {
                    response = "The product is not available";
                    break;
                }
                if (buyDetailDto.getQuantity() > product.getInInventory()){
                    response = "The amount requested is not in the inventory";
                    break;
                }
                if (buyDetailDto.getQuantity() < product.getMin()) {
                    response = "The amount requested doesn't have the minimum to buy";
                    break;
                }
                if (buyDetailDto.getQuantity() > product.getMax()) {
                    response = "The amount requested exceeds the maximum allowed per buy";
                    break;
                }
            }
        }
        return response;
    }
    /**
     * Method to discount quantities after each buy
     * @param buyDetailDtoList //productList
     */
    private void deductUnits(List<BuyDetailDto> buyDetailDtoList) {
        for (BuyDetailDto buyDetailDto: buyDetailDtoList){
            Optional<Product> productOptional = productRepository.findById(buyDetailDto.getProductId());
            if(productOptional.isPresent()){
                Product product = productOptional.get();
                product.setInInventory(product.getInInventory() - buyDetailDto.getQuantity());
                productRepository.save(product);
            }
        }
    }
}


















