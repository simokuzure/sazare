package com.sazare.service;

import com.sazare.vo.PageVO;
import com.sazare.vo.TagVO;

public interface TagService {

    PageVO<TagVO> listTags(String tagType, Long parentId, boolean enabledOnly, int page, int size);
}
