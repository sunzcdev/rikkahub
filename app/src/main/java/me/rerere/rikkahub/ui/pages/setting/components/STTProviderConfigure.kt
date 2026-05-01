package me.rerere.rikkahub.ui.pages.setting.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.R
import me.rerere.stt.provider.SttProviderSetting

@Composable
fun STTProviderConfigure(
    setting: SttProviderSetting,
    onValueChange: (SttProviderSetting) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState())
    ) {
        Text(
            text = stringResource(R.string.setting_stt_page_add_provider),
            style = MaterialTheme.typography.labelLarge
        )
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = setting.name,
            onValueChange = { onValueChange(setting.copyProvider(name = it)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.setting_stt_page_api_key),
            style = MaterialTheme.typography.labelLarge
        )
        Spacer(Modifier.height(4.dp))
        when (setting) {
            is SttProviderSetting.OpenAIWhisper -> {
                OutlinedTextField(
                    value = setting.apiKey,
                    onValueChange = {
                        onValueChange(setting.copy(apiKey = it))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.setting_stt_page_api_key_placeholder_openai)) }
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.setting_stt_page_base_url),
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = setting.baseUrl,
                    onValueChange = {
                        onValueChange(setting.copy(baseUrl = it))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            is SttProviderSetting.Gemini -> {
                OutlinedTextField(
                    value = setting.apiKey,
                    onValueChange = {
                        onValueChange(setting.copy(apiKey = it))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.setting_stt_page_base_url),
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = setting.baseUrl,
                    onValueChange = {
                        onValueChange(setting.copy(baseUrl = it))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        }
    }
}
