package ltldev.SeverUpFile.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

/**
 * Response DTO cho upload request
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UploadResult {

    private boolean success;
    private String message;
    private String errorCode;
    private int httpStatus;

    // Success fields
    private String filename;
    private Long fileSizeBytes;
    private Long durationMs;
    private Double speedMBps;

    public static UploadResult success(String message, String filename,
                                       long fileSizeBytes, long durationMs, double speedMBps) {
        return UploadResult.builder()
                .success(true)
                .message(message)
                .httpStatus(200)
                .filename(filename)
                .fileSizeBytes(fileSizeBytes)
                .durationMs(durationMs)
                .speedMBps(speedMBps)
                .build();
    }

    public static UploadResult error(String message, String errorCode, int httpStatus) {
        return UploadResult.builder()
                .success(false)
                .message(message)
                .errorCode(errorCode)
                .httpStatus(httpStatus)
                .build();
    }
}