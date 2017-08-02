package cn.cherish.springcloud.product.service.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 格式错误代码 400
 * 逻辑(ServiceException)错误代码 410*
 * 服务器内部错误代码 500*
 */
@AllArgsConstructor
@Getter
public enum ErrorCode {
    ERROR_CODE_400("EC400","请求参数错误"),

    ERROR_CODE_500_001("EC500001","服务器忙,请稍后再试"),
    ERROR_CODE_500_002("EC500002","资源忙，请稍后再试");
    private String code;

    private String desc;
}
