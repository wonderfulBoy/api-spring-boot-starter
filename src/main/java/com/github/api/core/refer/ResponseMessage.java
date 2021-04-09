package com.github.api.core.refer;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ResponseMessage
 *
 * @author echils
 * @since 2021-03-01 21:20:49
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
class ResponseMessage {

    private int code;

    private String message;
}
