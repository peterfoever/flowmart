package com.flowmart.product.service;

import com.flowmart.product.dto.SkuUpdateDTO;
import com.flowmart.product.vo.SkuDetailVO;
import com.flowmart.product.vo.SkuListVO;

import java.util.List;

public interface SkuService {

    List<SkuListVO> listSku(Long spuId);

    SkuDetailVO detailSku(Long id);

    void updateSku(Long skuId,SkuUpdateDTO request);
}
