package com.flowmart.product.service;

import com.flowmart.product.vo.SkuListVO;

import java.util.List;

public interface SkuService {

    List<SkuListVO> listSku(Long spuId);
}
