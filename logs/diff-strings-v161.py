import re, subprocess
head_xml = subprocess.run(['git', 'show', 'HEAD:app/src/main/res/values/strings.xml'],
    cwd=r'C:\Users\Sampath\.minimax-agent\projects\baton-v2-integration',
    capture_output=True, text=True).stdout
with open(r'app\src\main\res\values\strings.xml', 'r', encoding='utf-8') as f:
    cur_xml = f.read()
head_names = set(re.findall(r'name="(\w+)"', head_xml))
cur_names = set(re.findall(r'name="(\w+)"', cur_xml))
# Filter out strings that should be gone with the LLM drop
llm_drop = {
    'a11y_confidence_high', 'a11y_confidence_medium', 'a11y_confidence_low',
    'capture_sheet_extract', 'capture_sheet_confirm',
    'capture_sheet_llm_unavailable', 'capture_sheet_llm_unavailable_card_body',
    'capture_sheet_llm_unavailable_card_title', 'capture_sheet_llm_unavailable_save_plain',
    'confirmation_person', 'confirmation_action', 'confirmation_instruction_text',
    'confirmation_add_to_calendar',
    'model_download_title', 'model_download_subtitle', 'model_download_button',
    'model_download_percent', 'model_download_ready', 'model_download_size_mb',
    'model_download_continue', 'model_download_failed_reason', 'model_download_retry',
    'model_picker_title', 'model_picker_switch', 'model_picker_cancel', 'model_picker_confirm',
    'model_qwen3_1_7b_q4_k_m_name', 'model_qwen3_1_7b_q4_k_m_description',
    'model_llama_3_2_3b_instruct_q4_k_m_name', 'model_llama_3_2_3b_instruct_q4_k_m_description',
    'model_gemma_2_2b_it_q4_k_m_name', 'model_gemma_2_2b_it_q4_k_m_description',
    'model_phi_3_5_mini_3_8b_q4_k_m_name', 'model_phi_3_5_mini_3_8b_q4_k_m_description',
    'settings_section_models',
    'settings_model_llm', 'settings_model_whisper',
    'settings_model_not_downloaded', 'settings_model_downloading',
    'settings_model_ready', 'settings_model_ready_short', 'settings_model_download_short',
}
missing = sorted(head_names - cur_names - llm_drop)
print('Missing ({}):'.format(len(missing)))
for n in missing:
    print(' ', n)
