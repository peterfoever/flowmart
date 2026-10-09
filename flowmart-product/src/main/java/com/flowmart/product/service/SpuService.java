package com.flowmart.product.service;

import com.flowmart.common.result.PageResult;
import com.flowmart.product.dto.CreateSpuDTO;
import com.flowmart.product.dto.SpuQueryDTO;
import com.flowmart.product.dto.UpdateSpuDTO;
import com.flowmart.product.vo.SpuDetailVO;
import com.flowmart.product.vo.SpuListVO;

public interface SpuService {

    /**
     * 创建spu草稿
     * @param request
     * @return
     */
    Long createDraft(CreateSpuDTO request);

    SpuDetailVO getDetailById(Long id);

    void updateDraft(Long id , UpdateSpuDTO request);

    // spu分页查询
    PageResult<SpuListVO> page(SpuQueryDTO request);
}
