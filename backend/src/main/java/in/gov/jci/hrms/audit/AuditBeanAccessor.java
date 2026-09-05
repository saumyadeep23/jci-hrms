package in.gov.jci.hrms.audit;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * JPA instantiates AuditableEntityListener itself (it's not a Spring bean),
 * so there's no normal constructor/field injection point for it to reach
 * AuditLogRecorder. This holds a static reference to the ApplicationContext,
 * populated once at startup, that the listener uses to resolve it instead.
 */
@Component
public class AuditBeanAccessor implements ApplicationContextAware {

    private static volatile ApplicationContext context;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        context = applicationContext;
    }

    static <T> T getBean(Class<T> type) {
        ApplicationContext current = context;
        return current != null ? current.getBean(type) : null;
    }
}
