package com.flowmart.product.service;

import com.flowmart.product.dto.CreateSpuDTO;
import com.flowmart.product.vo.SpuDetailVO;

public interface SpuService {

    /**
     * 创建spu草稿
     * @param request
     * @return
     */
    Long createDraft(CreateSpuDTO request);

    SpuDetailVO getDetailById(Long id);
}
