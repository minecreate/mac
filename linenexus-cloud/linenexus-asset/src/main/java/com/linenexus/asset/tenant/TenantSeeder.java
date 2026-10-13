package com.linenexus.asset.tenant;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class TenantSeeder implements ApplicationRunner {

    private final TenantRepository repository;

    public TenantSeeder(TenantRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repository.existsById("tenant-dev")) {
            return;
        }
        TenantEntity t = new TenantEntity();
        t.setId("tenant-dev");
        t.setName("开发租户");
        t.setStatus("active");
        t.setRemark("本地开发默认租户");
        repository.save(t);
    }
}
