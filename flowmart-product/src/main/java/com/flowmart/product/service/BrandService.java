package com.flowmart.product.service;

import com.flowmart.product.dto.CreateBrandDTO;
import com.flowmart.product.vo.BrandVO;

public interface BrandService {

    Long createBrand(CreateBrandDTO request);

    BrandVO getBrand(Long id);
}
