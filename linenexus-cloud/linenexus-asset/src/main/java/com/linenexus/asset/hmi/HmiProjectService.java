package com.linenexus.asset.hmi;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class HmiProjectService {

    public static final String DEFAULT_PROJECT_ID = "hmi-proj-default";

    private final HmiProjectRepository repository;
    private final HmiScreenRepository screenRepository;

    public HmiProjectService(HmiProjectRepository repository, HmiScreenRepository screenRepository) {
        this.repository = repository;
        this.screenRepository = screenRepository;
    }

    @Transactional
    public List<HmiProject> list(String tenantId) {
        String tenant = blankToDefault(tenantId, "tenant-dev");
        ensureDefaultProject(tenant);
        return repository.findByTenantIdOrderBySortOrderAscUpdatedAtDesc(tenant).stream()
                .map(entity -> ensureHomeScreen(entity).toDto())
                .toList();
    }

    @Transactional
    public HmiProject get(String id) {
        return ensureHomeScreen(require(id)).toDto();
    }

    @Transactional
    public HmiProject create(HmiProjectRequest req) {
        if (req == null || req.name() == null || req.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name 不能为空");
        }
        String tenant = blankToDefault(req.tenantId(), "tenant-dev");
        String name = req.name().trim();
        if (repository.existsByTenantIdAndName(tenant, name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "工程名称已存在: " + name);
        }
        String id = blankToNull(req.id());
        if (id == null) {
            id = "hmi-proj-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        }
        if (repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "工程已存在: " + id);
        }
        HmiProjectEntity entity = new HmiProjectEntity();
        entity.setId(id);
        entity.setTenantId(tenant);
        entity.setName(name);
        entity.setSortOrder(req.sortOrder() == null ? 100 : req.sortOrder());
        repository.save(entity);

        HmiScreenEntity home = createScreenEntity(tenant, id, "主画面");
        entity.setHomeScreenId(home.getId());
        return repository.save(entity).toDto();
    }

    @Transactional
    public HmiProject update(String id, HmiProjectRequest req) {
        HmiProjectEntity entity = require(id);
        if (req == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求体不能为空");
        }
        if (req.name() != null && !req.name().isBlank()) {
            entity.setName(req.name().trim());
        }
        if (req.sortOrder() != null) {
            entity.setSortOrder(req.sortOrder());
        }
        if (req.homeScreenId() != null && !req.homeScreenId().isBlank()) {
            setHomeScreen(entity, req.homeScreenId().trim());
        }
        return repository.save(entity).toDto();
    }

    /** 将指定画面设为工程启动主画面。 */
    @Transactional
    public HmiProject setHomeScreen(String projectId, String screenId) {
        HmiProjectEntity entity = require(projectId);
        setHomeScreen(entity, screenId);
        return repository.save(entity).toDto();
    }

    @Transactional
    public void delete(String id) {
        HmiProjectEntity entity = require(id);
        if (DEFAULT_PROJECT_ID.equals(entity.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "默认工程不可删除");
        }
        String tenant = entity.getTenantId();
        ensureDefaultProject(tenant);
        HmiProjectEntity defaultProject = require(DEFAULT_PROJECT_ID);
        for (HmiScreenEntity screen :
                screenRepository.findByTenantIdAndProjectIdOrderByUpdatedAtDesc(tenant, id)) {
            screen.setProjectId(DEFAULT_PROJECT_ID);
            screenRepository.save(screen);
        }
        ensureHomeScreen(defaultProject);
        repository.deleteById(id);
    }

    /**
     * 删除画面前校验：主画面不可删；若误删会抛错。
     * 由 ScreenService 调用。
     */
    @Transactional
    public void assertScreenDeletable(String screenId) {
        HmiScreenEntity screen = screenRepository
                .findById(screenId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "画面不存在: " + screenId));
        if (screen.getProjectId() == null || screen.getProjectId().isBlank()) {
            return;
        }
        repository
                .findById(screen.getProjectId())
                .ifPresent(project -> {
                    if (screenId.equals(project.getHomeScreenId())) {
                        throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST, "主画面不可删除，请先将其它画面设为启动首页");
                    }
                });
    }

    @Transactional
    public String ensureDefaultProject(String tenantId) {
        String tenant = blankToDefault(tenantId, "tenant-dev");
        HmiProjectEntity entity = repository
                .findByIdAndTenantId(DEFAULT_PROJECT_ID, tenant)
                .or(() -> repository.findById(DEFAULT_PROJECT_ID))
                .orElseGet(() -> {
                    HmiProjectEntity created = new HmiProjectEntity();
                    created.setId(DEFAULT_PROJECT_ID);
                    created.setTenantId(tenant);
                    created.setName("默认工程");
                    created.setSortOrder(0);
                    return repository.save(created);
                });
        return ensureHomeScreen(entity).getId();
    }

    private void setHomeScreen(HmiProjectEntity entity, String screenId) {
        HmiScreenEntity screen = screenRepository
                .findById(screenId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "画面不存在: " + screenId));
        if (!entity.getId().equals(screen.getProjectId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "目标画面不属于本工程");
        }
        entity.setHomeScreenId(screenId);
    }

    /** 保证工程有且可解析到一张主画面；缺失则创建或回填。 */
    private HmiProjectEntity ensureHomeScreen(HmiProjectEntity entity) {
        String homeId = blankToNull(entity.getHomeScreenId());
        if (homeId != null && screenRepository.existsById(homeId)) {
            return entity;
        }
        List<HmiScreenEntity> screens =
                screenRepository.findByTenantIdAndProjectIdOrderByUpdatedAtDesc(
                        entity.getTenantId(), entity.getId());
        if (!screens.isEmpty()) {
            // 取最早创建的作为主画面（更贴近「启动首页」）
            HmiScreenEntity oldest = screens.stream()
                    .min(Comparator.comparing(
                            HmiScreenEntity::getCreatedAt,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .orElse(screens.get(screens.size() - 1));
            entity.setHomeScreenId(oldest.getId());
            return repository.save(entity);
        }
        HmiScreenEntity home = createScreenEntity(entity.getTenantId(), entity.getId(), "主画面");
        entity.setHomeScreenId(home.getId());
        return repository.save(entity);
    }

    private HmiScreenEntity createScreenEntity(String tenantId, String projectId, String name) {
        HmiScreenEntity screen = new HmiScreenEntity();
        screen.setId("hmi-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        screen.setTenantId(tenantId);
        screen.setProjectId(projectId);
        screen.setName(name);
        screen.setVersion(1);
        screen.setStatus("draft");
        screen.setDocumentJson(HmiDocuments.emptyDocumentJson());
        screen.setUpdatedBy("system");
        return screenRepository.save(screen);
    }

    private HmiProjectEntity require(String id) {
        return repository
                .findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "工程不存在: " + id));
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
