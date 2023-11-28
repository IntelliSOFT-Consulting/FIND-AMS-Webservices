package com.intellisoft.findams.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@NoArgsConstructor
@Getter
@Setter
@ToString
public class FileParseSummaryDto {
    String responseType;
    String status;
    int imported;
    int updated;
    int deleted;
    int ignored;
}
