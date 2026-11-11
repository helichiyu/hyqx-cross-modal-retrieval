package com.hsmty.cmr.config;

import com.hsmty.cmr.common.auth.AuthContext;
import com.hsmty.cmr.common.entity.BaseEntity;
import com.mybatisflex.annotation.InsertListener;
import com.mybatisflex.annotation.UpdateListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 审计字段填充：插入时补 createId/createTime/delFlag，更新时补 updateId/updateTime
 * （等价 hsmty EntityChangeListener；delFlag 逻辑删除初值由 Flex 全局配置维护，此处兜底）
 *
 * <p>主键 {@code id} 由各业务服务显式赋值（注入 {@code SnowflakeIdWorker#next()}），
 * 监听器不负责生成：MyBatis-Flex 反射实例化监听器时不走 Spring 容器，无法注入依赖。
 *
 * @author cmr
 * @since 2026-11-03
 */
@Component
public class AuditListener implements InsertListener, UpdateListener {

    @Override
    public void onInsert(Object entity) {
        if (entity instanceof BaseEntity e) {
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
