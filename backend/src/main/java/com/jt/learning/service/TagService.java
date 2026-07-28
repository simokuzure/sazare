package com.jt.learning.service;

import com.jt.learning.vo.PageVO;
import com.jt.learning.vo.TagVO;

public interface TagService {

    PageVO<TagVO> listTags(String tagType, Long parentId, boolean enabledOnly, int page, int size);
}
