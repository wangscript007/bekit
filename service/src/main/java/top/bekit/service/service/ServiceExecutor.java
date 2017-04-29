/* 
 * 作者：钟勋 (e-mail:zhongxunking@163.com)
 */

/*
 * 修订记录:
 * @author 钟勋 2016-12-16 01:14 创建
 */
package top.bekit.service.service;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.ClassUtils;
import top.bekit.common.method.MethodExecutor;
import top.bekit.common.transaction.TxExecutor;
import top.bekit.service.annotation.service.ServiceCheck;
import top.bekit.service.annotation.service.ServiceExecute;
import top.bekit.service.engine.ServiceContext;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * 服务执行器
 */
public class ServiceExecutor {
    /**
     * 服务方法注解
     */
    public static final Class[] SERVICE_METHOD_ANNOTATIONS = {ServiceCheck.class, ServiceExecute.class};

    // 服务名称
    private String serviceName;
    // 是否开启事务
    private boolean enableTx;
    // 服务
    private Object service;
    // 服务方法执行器Map（key：服务方法注解的Class）
    private Map<Class, ServiceMethodExecutor> methodExecutorMap = new HashMap<>();
    // 事务执行器
    private TxExecutor txExecutor;

    public ServiceExecutor(String serviceName, boolean enableTx, Object service) {
        this.serviceName = serviceName;
        this.enableTx = enableTx;
        this.service = service;
    }

    /**
     * 执行服务
     *
     * @param serviceContext 服务上下文
     * @throws Throwable 执行过程中发生任何异常都会往外抛
     */
    public void execute(ServiceContext serviceContext) throws Throwable {
        // 执行服务校验方法（如果存在）
        if (methodExecutorMap.containsKey(ServiceCheck.class)) {
            methodExecutorMap.get(ServiceCheck.class).execute(service, serviceContext);
        }
        if (enableTx) {
            // 开启事务
            txExecutor.createTx();
        }
        try {
            // 执行服务执行方法
            methodExecutorMap.get(ServiceExecute.class).execute(service, serviceContext);
            if (enableTx) {
                // 提交事务
                txExecutor.commitTx();
            }
        } catch (Throwable e) {
            if (enableTx) {
                // 回滚事务
                txExecutor.rollbackTx();
            }
            throw e;
        }
    }

    /**
     * 设置服务方法执行器
     *
     * @param clazz                 服务方法注解的class
     * @param serviceMethodExecutor 服务方法执行器
     * @throws IllegalArgumentException 如果入参class不是服务方法注解
     * @throws IllegalStateException    如果已存在该类型的服务方法处理器
     */
    public void setMethodExecutor(Class clazz, ServiceMethodExecutor serviceMethodExecutor) {
        if (!Arrays.asList(SERVICE_METHOD_ANNOTATIONS).contains(clazz)) {
            throw new IllegalArgumentException(ClassUtils.getShortName(clazz) + "不是服务方法注解");
        }
        if (methodExecutorMap.containsKey(clazz)) {
            throw new IllegalStateException("服务" + serviceName + "存在多个@" + ClassUtils.getShortName(clazz) + "类型方法");
        }
        methodExecutorMap.put(clazz, serviceMethodExecutor);
    }

    /**
     * 设置事务管理器
     *
     * @param txManager 事务管理器
     */
    public void setTxManager(PlatformTransactionManager txManager) {
        if (!enableTx) {
            throw new IllegalStateException("服务" + serviceName + "的enableTx属性为关闭状态，不能设置事务");
        }
        if (txExecutor != null) {
            throw new IllegalStateException("服务" + serviceName + "的事务管理器已经被设置，不能重复设置");
        }
        txExecutor = new TxExecutor(txManager);
    }

    /**
     * 获取服务名称
     */
    public String getServiceName() {
        return serviceName;
    }

    /**
     * 获取Order的真实类型
     */
    public Class getOrderClass() {
        return methodExecutorMap.get(ServiceExecute.class).getOrderClass();
    }

    /**
     * 获取Result的真实类型
     */
    public Class getResultClass() {
        return methodExecutorMap.get(ServiceExecute.class).getResultClass();
    }

    /**
     * 校验服务执行器是否有效
     *
     * @throws IllegalStateException 校验不通过
     */
    public void validate() {
        if (serviceName == null || service == null) {
            throw new IllegalStateException("服务" + serviceName + "内部要素不全");
        }
        if (enableTx) {
            if (txExecutor == null) {
                throw new IllegalStateException("服务" + serviceName + "的enableTx属性为开启状态，但未设置事务执行器");
            }
            txExecutor.validate();
        } else {
            if (txExecutor != null) {
                throw new IllegalStateException("服务" + serviceName + "的enableTx属性为关闭状态，但设置了事务执行器");
            }
        }
        if (!methodExecutorMap.containsKey(ServiceExecute.class)) {
            throw new IllegalStateException("服务" + serviceName + "缺少@ServiceExecute类型方法");
        }
        // 校验服务内的ServiceContext泛型类型是否统一
        for (ServiceMethodExecutor methodExecutor : methodExecutorMap.values()) {
            if (methodExecutor.getOrderClass() != getOrderClass()) {
                throw new IllegalStateException("服务" + serviceName + "内的ServiceContext的泛型类型不统一");
            }
            if (methodExecutor.getResultClass() != getResultClass()) {
                throw new IllegalStateException("服务" + serviceName + "内的ServiceContext的泛型类型不统一");
            }
        }
    }

    /**
     * 服务方法执行器
     */
    public static class ServiceMethodExecutor extends MethodExecutor {
        // ServiceContext泛型O的真实类型
        private Class orderClass;
        // ServiceContext泛型R的真实类型
        private Class resultClass;

        public ServiceMethodExecutor(Method targetMethod, Class orderClass, Class resultClass) {
            super(targetMethod);
            this.orderClass = orderClass;
            this.resultClass = resultClass;
        }

        /**
         * 执行服务方法
         *
         * @param service        服务
         * @param serviceContext 服务上下文
         * @throws Throwable 执行过程中发生任何异常都会往外抛
         */
        public void execute(Object service, ServiceContext serviceContext) throws Throwable {
            execute(service, new Object[]{serviceContext});
        }

        /**
         * 获取ServiceContext泛型O的真实类型
         */
        public Class getOrderClass() {
            return orderClass;
        }

        /**
         * 获取ServiceContext泛型R的真实类型
         */
        public Class getResultClass() {
            return resultClass;
        }
    }
}
