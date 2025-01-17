package org.apereo.cas.web.flow;

import org.apereo.cas.authentication.principal.Service;
import org.apereo.cas.authentication.principal.WebApplicationService;
import org.apereo.cas.util.function.FunctionUtils;
import org.apereo.cas.util.spring.beans.BeanSupplier;
import org.apereo.cas.web.DelegatedClientIdentityProviderConfiguration;
import org.apereo.cas.web.DelegatedClientIdentityProviderConfigurationFactory;
import org.apereo.cas.web.support.WebUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.pac4j.core.client.Client;
import org.pac4j.core.client.IndirectClient;
import org.pac4j.jee.context.JEEContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.webflow.execution.RequestContext;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This is {@link DefaultDelegatedClientIdentityProviderConfigurationProducer}.
 *
 * @author Misagh Moayyed
 * @since 6.2.0
 */
@Slf4j
@RequiredArgsConstructor
public class DefaultDelegatedClientIdentityProviderConfigurationProducer implements DelegatedClientIdentityProviderConfigurationProducer {
    private final ObjectProvider<DelegatedClientAuthenticationConfigurationContext> configurationContext;

    @Override
    public Set<DelegatedClientIdentityProviderConfiguration> produce(final RequestContext context) {
        val currentService = WebUtils.getService(context);

        val selectionStrategies = configurationContext.getObject().getAuthenticationRequestServiceSelectionStrategies();
        val service = selectionStrategies.resolveService(currentService, WebApplicationService.class);
        val request = WebUtils.getHttpServletRequestFromExternalWebflowContext(context);
        val response = WebUtils.getHttpServletResponseFromExternalWebflowContext(context);
        val webContext = new JEEContext(request, response);

        LOGGER.debug("Initialized context with request parameters [{}]", webContext.getRequestParameters());

        val clients = configurationContext.getObject().getClients();
        val allClients = clients.findAllClients();
        val providers = allClients
            .stream()
            .filter(client -> client instanceof IndirectClient
                              && isDelegatedClientAuthorizedForService(client, service, context))
            .map(IndirectClient.class::cast)
            .map(client -> produce(context, client))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toCollection(LinkedHashSet::new));

        val delegatedClientIdentityProviderRedirectionStrategy = configurationContext.getObject().getDelegatedClientIdentityProviderRedirectionStrategy();
        delegatedClientIdentityProviderRedirectionStrategy.select(context, service, providers)
            .ifPresent(p -> WebUtils.putDelegatedAuthenticationProviderPrimary(context, p));

        if (!providers.isEmpty()) {
            val casProperties = configurationContext.getObject().getCasProperties();
            val selectionType = casProperties.getAuthn().getPac4j().getCore().getDiscoverySelection().getSelectionType();
            switch (selectionType) {
                case DYNAMIC:
                    WebUtils.putDelegatedAuthenticationProviderConfigurations(context, new HashSet<>());
                    WebUtils.putDelegatedAuthenticationDynamicProviderSelection(context, Boolean.TRUE);
                    break;
                case MENU:
                default:
                    WebUtils.putDelegatedAuthenticationProviderConfigurations(context, providers);
                    WebUtils.putDelegatedAuthenticationDynamicProviderSelection(context, Boolean.FALSE);
                    break;
            }

        } else if (response.getStatus() != HttpStatus.UNAUTHORIZED.value()) {
            LOGGER.warn("No delegated authentication providers could be determined based on the provided configuration. "
                        + "Either no clients are configured, or the current access strategy rules prohibit CAS from using authentication providers");
        }
        return providers;
    }

    @Override
    public Optional<DelegatedClientIdentityProviderConfiguration> produce(final RequestContext requestContext,
                                                                          final IndirectClient client) {
        return FunctionUtils.doAndHandle(() -> {
            val request = WebUtils.getHttpServletRequestFromExternalWebflowContext(requestContext);
            val response = WebUtils.getHttpServletResponseFromExternalWebflowContext(requestContext);
            val webContext = new JEEContext(request, response);

            val currentService = WebUtils.getService(requestContext);
            LOGGER.debug("Initializing client [{}] with request parameters [{}] and service [{}]",
                client, requestContext.getRequestParameters(), currentService);
            client.init();

            val customizers = configurationContext.getObject().getDelegatedClientAuthenticationRequestCustomizers();
            if (customizers.isEmpty() || customizers.stream()
                .filter(BeanSupplier::isNotProxy)
                .anyMatch(c -> c.isAuthorized(webContext, client, currentService))) {
                return DelegatedClientIdentityProviderConfigurationFactory.builder()
                    .client(client)
                    .webContext(webContext)
                    .service(currentService)
                    .casProperties(configurationContext.getObject().getCasProperties())
                    .build()
                    .resolve();
            }
            return Optional.<DelegatedClientIdentityProviderConfiguration>empty();
        }, throwable -> Optional.<DelegatedClientIdentityProviderConfiguration>empty()).get();
    }

    protected boolean isDelegatedClientAuthorizedForService(final Client client,
                                                            final Service service,
                                                            final RequestContext context) {
        return configurationContext.getObject().getDelegatedClientIdentityProviderAuthorizers()
            .stream()
            .allMatch(authz -> authz.isDelegatedClientAuthorizedForService(client, service, context));
    }
}
