package com.flowmart.product.service;

import com.flowmart.product.dto.CreateBrandDTO;
import com.flowmart.product.vo.BrandVO;

public interface BrandService {

    Long creatBrand(CreateBrandDTO request);

    BrandVO getBrand(Long id);
}
