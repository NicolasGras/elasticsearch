/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.application.connector.secrets;

import org.elasticsearch.ResourceNotFoundException;
import org.elasticsearch.Version;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.admin.indices.template.put.PutIndexTemplateRequest;
import org.elasticsearch.client.internal.Client;
import org.elasticsearch.client.internal.OriginSettingClient;
import org.elasticsearch.indices.SystemIndexDescriptor;
import org.elasticsearch.xcontent.XContentType;
import org.elasticsearch.xpack.application.connector.secrets.action.GetConnectorSecretResponse;
import org.elasticsearch.xpack.application.connector.secrets.action.PostConnectorSecretRequest;
import org.elasticsearch.xpack.application.connector.secrets.action.PostConnectorSecretResponse;
import org.elasticsearch.xpack.core.template.TemplateUtils;

import java.util.Map;

import static org.elasticsearch.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.xpack.core.ClientHelper.CONNECTORS_ORIGIN;

/**
 * A service that manages persistent Connector Secrets.
 */
public class ConnectorSecretsIndexService {

    private final Client clientWithOrigin;

    public static final String CONNECTOR_SECRETS_INDEX_NAME = ".connector-secrets";
    private static final int CURRENT_INDEX_VERSION = 1;
    private static final String MAPPING_VERSION_VARIABLE = "connector-secrets.version";
    private static final String MAPPING_MANAGED_VERSION_VARIABLE = "connector-secrets.managed.index.version";

    public ConnectorSecretsIndexService(Client client) {
        this.clientWithOrigin = new OriginSettingClient(client, CONNECTORS_ORIGIN);
    }

    /**
     * Returns the {@link SystemIndexDescriptor} for the Connector Secrets system index.
     *
     * @return The {@link SystemIndexDescriptor} for the Connector Secrets system index.
     */
    public static SystemIndexDescriptor getSystemIndexDescriptor() {
        PutIndexTemplateRequest request = new PutIndexTemplateRequest();

        String templateSource = TemplateUtils.loadTemplate(
            "/connector-secrets.json",
            Version.CURRENT.toString(),
            MAPPING_VERSION_VARIABLE,
            Map.of(MAPPING_MANAGED_VERSION_VARIABLE, Integer.toString(CURRENT_INDEX_VERSION))
        );
        request.source(templateSource, XContentType.JSON);

        return SystemIndexDescriptor.builder()
            .setIndexPattern(CONNECTOR_SECRETS_INDEX_NAME + "*")
            .setPrimaryIndex(CONNECTOR_SECRETS_INDEX_NAME + "-" + CURRENT_INDEX_VERSION)
            .setDescription("Secret values managed by Connectors")
            .setMappings(request.mappings())
            .setSettings(request.settings())
            .setAliasName(CONNECTOR_SECRETS_INDEX_NAME)
            .setVersionMetaKey("version")
            .setOrigin(CONNECTORS_ORIGIN)
            .setType(SystemIndexDescriptor.Type.INTERNAL_MANAGED)
            .build();
    }

    public void getSecret(String id, ActionListener<GetConnectorSecretResponse> listener) {
        clientWithOrigin.prepareGet(CONNECTOR_SECRETS_INDEX_NAME, id).execute(listener.delegateFailureAndWrap((delegate, getResponse) -> {
            if (getResponse.isSourceEmpty()) {
                delegate.onFailure(new ResourceNotFoundException("No secret with id [" + id + "]"));
                return;
            }
            delegate.onResponse(new GetConnectorSecretResponse(getResponse.getId(), getResponse.getSource().get("value").toString()));
        }));
    }

    public void createSecret(PostConnectorSecretRequest request, ActionListener<PostConnectorSecretResponse> listener) {
        try {
            clientWithOrigin.prepareIndex(CONNECTOR_SECRETS_INDEX_NAME)
                .setSource(request.toXContent(jsonBuilder()))
                .execute(
                    listener.delegateFailureAndWrap(
                        (l, indexResponse) -> l.onResponse(new PostConnectorSecretResponse(indexResponse.getId()))
                    )
                );
        } catch (Exception e) {
            listener.onFailure(e);
        }
    }
}
