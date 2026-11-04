package com.hsmty.cmr.config;

import com.hsmty.cmr.common.auth.AuthContext;
import com.hsmty.cmr.common.entity.BaseEntity;
import com.hsmty.cmr.common.id.SnowflakeIdWorker;
import com.mybatisflex.annotation.InsertListener;
import com.mybatisflex.annotation.UpdateListener;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 审计字段填充：插入时补 id/createId/createTime/delFlag，更新时补 updateId/updateTime
 * （等价 hsmty EntityChangeListener；delFlag 逻辑删除初值由 Flex 全局配置维护，此处兜底）
 *
 * @author cmr
 * @since 2026-11-03
 */
@Component
@RequiredArgsConstructor
public class AuditListener implements InsertListener, UpdateListener {

    private final SnowflakeIdWorker idWorker;

    @Override
    public void onInsert(Object entity) {
        if (entity instanceof BaseEntity e) {
            if (e.getId() == null) {
                e.setId(idWorker.next());
            }
            if (e.getCreateId() == null) {
                e.setCreateId(AuthContext.userId());
            }
            if (e.getCreateTime() == null) {
                e.setCreateTime(LocalDateTime.now());
            }
            if (e.getDelFlag() == null) {
                e.setDelFlag("F");
            }
        }
    }

    @Override
    public void onUpdate(Object entity) {
        if (entity instanceof BaseEntity e) {
            e.setUpdateId(AuthContext.userId());
            e.setUpdateTime(LocalDateTime.now());
        }
    }
}
