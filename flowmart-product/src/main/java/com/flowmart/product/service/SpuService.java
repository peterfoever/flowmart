package com.flowmart.product.service;

import com.flowmart.product.dto.CreateSpuDTO;

public interface SpuService {

    /**
     * 创建spu草稿
     * @param request
     * @return
     */
    Long createDraft(CreateSpuDTO request);
}
