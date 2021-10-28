package software.amazon.memorydb.user;

import software.amazon.cloudformation.proxy.StdCallbackContext;

@lombok.Data
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
@lombok.Getter
@lombok.Setter
@lombok.ToString
@lombok.EqualsAndHashCode(callSuper = true)
public class CallbackContext extends StdCallbackContext {
    private Integer retriesRemaining;
}
