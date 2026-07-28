package com.jt.learning.controller;

import com.jt.learning.common.ApiResponse;
import com.jt.learning.service.TagService;
import com.jt.learning.vo.PageVO;
import com.jt.learning.vo.TagVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TagController {

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    @GetMapping("/tags")
    public ApiResponse<PageVO<TagVO>> listTags(
            @RequestParam(required = false) String tagType,
            @RequestParam(required = false) Long parentId,
            @RequestParam(defaultValue = "true") boolean enabledOnly,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(tagService.listTags(tagType, parentId, enabledOnly, page, size));
    }
}
