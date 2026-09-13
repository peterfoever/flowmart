package com.flowmart.product.service;

import com.flowmart.product.dto.CreateBrandDTO;
import com.flowmart.product.dto.UpdateBrandDTO;
import com.flowmart.product.dto.UpdateBrandStatusDTO;
import com.flowmart.product.vo.BrandVO;

public interface BrandService {

    Long createBrand(CreateBrandDTO request);

    BrandVO getBrand(Long id);

    void updateBrand(Long id, UpdateBrandDTO request);

    void updateBrandStatus(Long id, UpdateBrandStatusDTO request);
}
