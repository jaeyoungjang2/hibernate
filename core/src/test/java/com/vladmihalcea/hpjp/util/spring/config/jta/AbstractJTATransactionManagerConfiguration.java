package com.vladmihalcea.hpjp.util.spring.config.jta;

import com.atomikos.icatch.jta.UserTransactionManager;
import com.vladmihalcea.hpjp.util.DataSourceProxyType;
import com.vladmihalcea.hpjp.util.logging.InlineQueryLogEntryCreator;
import jakarta.transaction.SystemException;
import net.ttddyy.dsproxy.listener.logging.SLF4JQueryLoggingListener;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.engine.transaction.jta.platform.internal.AtomikosJtaPlatform;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.orm.jpa.JpaVendorAdapter;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.jta.JtaTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.Properties;

/**
 * @author Vlad Mihalcea
 */
@EnableTransactionManagement
@EnableAspectJAutoProxy
public abstract class AbstractJTATransactionManagerConfiguration {

    public static final String DATA_SOURCE_PROXY_NAME = DataSourceProxyType.DATA_SOURCE_PROXY.name();

    @Bean
    public static PropertySourcesPlaceholderConfigurer properties() {
        return new PropertySourcesPlaceholderConfigurer();
    }

    @Bean
    public abstract DataSource actualDataSource();

    @DependsOn("actualDataSource")
    public DataSource dataSource() {
        SLF4JQueryLoggingListener loggingListener = new SLF4JQueryLoggingListener();
        loggingListener.setQueryLogEntryCreator(new InlineQueryLogEntryCreator());
        return ProxyDataSourceBuilder
                .create(actualDataSource())
                .name(DATA_SOURCE_PROXY_NAME)
                .listener(loggingListener)
                .build();
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory() {
        LocalContainerEntityManagerFactoryBean localContainerEntityManagerFactoryBean = new LocalContainerEntityManagerFactoryBean();
        localContainerEntityManagerFactoryBean.setJtaDataSource(dataSource());
        localContainerEntityManagerFactoryBean.setPackagesToScan(packagesToScan());

        JpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        localContainerEntityManagerFactoryBean.setJpaVendorAdapter(vendorAdapter);
        localContainerEntityManagerFactoryBean.setJpaProperties(additionalProperties());
        return localContainerEntityManagerFactoryBean;
    }

    protected String[] packagesToScan() {
        return new String[]{
            configurationClass().getPackage().getName()
        };
    }

    protected abstract Class configurationClass();

    @Bean(initMethod = "init", destroyMethod = "close")
    public UserTransactionManager userTransactionManager() throws SystemException {
        UserTransactionManager userTransactionManager = new UserTransactionManager();
        userTransactionManager.setTransactionTimeout(300);
        userTransactionManager.setForceShutdown(true);
        return userTransactionManager;
    }

    @Bean
    public JtaTransactionManager transactionManager() throws SystemException {
        JtaTransactionManager jtaTransactionManager = new JtaTransactionManager();
        jtaTransactionManager.setTransactionManager(userTransactionManager());
        jtaTransactionManager.setUserTransaction(userTransactionManager());
        jtaTransactionManager.setAllowCustomIsolationLevels(true);
        return jtaTransactionManager;
    }

    @Bean
    public TransactionTemplate transactionTemplate() throws SystemException {
        return new TransactionTemplate(transactionManager());
    }

    protected Properties additionalProperties() {
        Properties properties = new Properties();
        properties.put(
            AvailableSettings.JTA_PLATFORM,
            AtomikosJtaPlatform.class
        );
        properties.setProperty("hibernate.hbm2ddl.auto", "create-drop");
        return properties;
    }
}
