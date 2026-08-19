package com.conice.morss.infrastructure.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.conice.morss.infrastructure.ai.provider.AiProviderAdapter
import com.conice.morss.infrastructure.ai.provider.AiProviderRegistry
import com.conice.morss.infrastructure.ai.provider.AnthropicAdapter
import com.conice.morss.infrastructure.ai.provider.GeminiAdapter
import com.conice.morss.infrastructure.ai.provider.ResponsesAdapter
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AiProviderModule {
    @Provides
    @Singleton
    fun provideResponsesAdapter(client: OkHttpClient): ResponsesAdapter = ResponsesAdapter(client)

    @Provides
    @Singleton
    fun provideGeminiAdapter(client: OkHttpClient): GeminiAdapter = GeminiAdapter(client)

    @Provides
    @Singleton
    fun provideAnthropicAdapter(client: OkHttpClient): AnthropicAdapter = AnthropicAdapter(client)

    @Provides
    @Singleton
    fun provideAiProviderRegistry(
        responses: ResponsesAdapter,
        gemini: GeminiAdapter,
        anthropic: AnthropicAdapter,
    ): AiProviderRegistry = AiProviderRegistry(
        adapters = listOf<AiProviderAdapter>(responses, gemini, anthropic),
    )
}

