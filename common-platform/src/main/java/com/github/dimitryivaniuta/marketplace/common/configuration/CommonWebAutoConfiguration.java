package com.github.dimitryivaniuta.marketplace.common.configuration;

import com.github.dimitryivaniuta.marketplace.common.web.CorrelationWebFilter;
import com.github.dimitryivaniuta.marketplace.common.web.GlobalErrorHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Import;

/** Auto-configures shared correlation and safe WebFlux error handling. */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@Import({CorrelationWebFilter.class, GlobalErrorHandler.class})
public class CommonWebAutoConfiguration {
}
