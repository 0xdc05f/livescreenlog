<script lang="ts">
  import { onMount } from 'svelte';
  import { t } from '../i18n';

  let { projects = $bindable([]), onClose } = $props();

  let loading = $state(false);
  let error = $state('');
  let copiedKey = $state<string | null>(null);
  let searchQuery = $state('');

  // --- Unified Create/Edit Modal ---
  let showModal = $state(false);
  let modalTitle = $state('');
  let isEditing = $state(false);
  
  // Form Fields
  let projectId = $state<number | null>(null);
  let projectName = $state('');
  let projectDesc = $state('');
  let projectApiKey = $state('');
  let recordingMode = $state('ALL');
  let targetUsers = $state('');
  
  let saving = $state(false);
  let formError = $state('');

  // Mode B active terminals (standby)
  let activeTerminals = $state<string[]>([]);
  let loadingTerminals = $state(false);
  let triggeringUser = $state<string | null>(null);
  let triggerResults = $state<Record<string, string>>({});

  async function loadProjects() {
    loading = true;
    try {
      const res = await fetch('/api/projects');
      if (res.ok) projects = await res.json();
    } catch {
      error = $t.projectLoadError;
    } finally {
      loading = false;
    }
  }

  function openCreateModal() {
    isEditing = false;
    modalTitle = $t.projectCreateTitle;
    projectId = null;
    projectName = '';
    projectDesc = '';
    projectApiKey = '';
    recordingMode = 'ALL';
    targetUsers = '';
    formError = '';
    showModal = true;
  }

  function openEditModal(project: any) {
    isEditing = true;
    modalTitle = $t.projectSettingsTitle;
    projectId = project.id;
    projectName = project.name;
    projectDesc = project.description ?? '';
    projectApiKey = project.apiKey;
    recordingMode = project.recordingMode ?? 'ALL';
    targetUsers = project.targetUsers ?? '';
    formError = '';
    activeTerminals = [];
    triggerResults = {};
    showModal = true;

    if (recordingMode === 'B') {
      fetchActiveTerminals(project.apiKey);
    }
  }

  async function handleSave() {
    if (!projectName.trim()) {
      formError = $t.projectNameRequired;
      return;
    }
    saving = true;
    formError = '';

    try {
      if (isEditing && projectId) {
        // 1. Update project details & settings
        const res = await fetch(`/api/projects/${projectId}/settings`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            recordingMode,
            targetUsers: recordingMode === 'A' ? targetUsers : null
          })
        });

        if (res.ok) {
          const updated = await res.json();
          projects = projects.map((p: any) => p.id === updated.id ? updated : p);
          showModal = false;
        } else {
          formError = $t.projectSettingsError;
        }
      } else {
        // 2. Create new project (mode defaults to ALL)
        const res = await fetch('/api/projects', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            name: projectName.trim(),
            description: projectDesc.trim()
          })
        });

        if (res.ok) {
          const created = await res.json();
          // If a specific mode other than default ALL was selected during creation, apply it
          if (recordingMode !== 'ALL' || targetUsers) {
            const settingsRes = await fetch(`/api/projects/${created.id}/settings`, {
              method: 'PUT',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({
                recordingMode,
                targetUsers: recordingMode === 'A' ? targetUsers : null
              })
            });
            if (settingsRes.ok) {
              const updated = await settingsRes.json();
              projects = [updated, ...projects];
            } else {
              projects = [created, ...projects];
            }
          } else {
            projects = [created, ...projects];
          }
          showModal = false;
        } else {
          formError = $t.projectCreateError;
        }
      }
    } catch {
      formError = isEditing ? $t.projectSettingsError : $t.projectCreateError;
    } finally {
      saving = false;
    }
  }

  async function deleteProject(id: number) {
    if (!confirm($t.projectDeleteConfirm)) return;
    try {
      const res = await fetch(`/api/projects/${id}`, { method: 'DELETE' });
      if (res.ok) projects = projects.filter((p: any) => p.id !== id);
    } catch {}
  }

  async function copyKey(key: string) {
    try {
      await navigator.clipboard.writeText(key);
      copiedKey = key;
      setTimeout(() => copiedKey = null, 2000);
    } catch {}
  }

  async function fetchActiveTerminals(projectKey: string) {
    loadingTerminals = true;
    try {
      const res = await fetch(`/api/push/active-terminals?projectKey=${encodeURIComponent(projectKey)}`);
      if (res.ok) activeTerminals = await res.json();
    } catch {
      activeTerminals = [];
    } finally {
      loadingTerminals = false;
    }
  }

  async function triggerRecording(userId: string) {
    if (!projectApiKey) return;
    triggeringUser = userId;
    triggerResults = { ...triggerResults, [userId]: 'triggering' };
    try {
      const res = await fetch(
        `/api/push/trigger-record?projectKey=${encodeURIComponent(projectApiKey)}&userId=${encodeURIComponent(userId)}`,
        { method: 'POST' }
      );
      if (res.ok) {
        triggerResults = { ...triggerResults, [userId]: 'triggered' };
        setTimeout(() => {
          activeTerminals = activeTerminals.filter(u => u !== userId);
          triggerResults = Object.fromEntries(
            Object.entries(triggerResults).filter(([k]) => k !== userId)
          );
        }, 3000);
      } else {
        triggerResults = { ...triggerResults, [userId]: 'error' };
      }
    } catch {
      triggerResults = { ...triggerResults, [userId]: 'error' };
    } finally {
      triggeringUser = null;
    }
  }

  function modeShortLabel(mode: string | undefined): string {
    const m = mode ?? 'ALL';
    const map: Record<string, string> = {
      ALL: $t.projectModeShortAll,
      NONE: $t.projectModeShortNone,
      A: $t.projectModeShortA,
      B: $t.projectModeShortB,
      C: $t.projectModeShortC,
    };
    return map[m] ?? m;
  }

  onMount(() => {
    loadProjects();
  });

  let filteredProjects = $derived(
    projects.filter((p: any) =>
      p.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
      (p.description && p.description.toLowerCase().includes(searchQuery.toLowerCase()))
    )
  );

  const modeOptions = [
    { value: 'ALL', label: () => $t.projectModeAll, desc: () => $t.projectModeAllDesc },
    { value: 'NONE', label: () => $t.projectModeNone, desc: () => $t.projectModeNoneDesc },
    { value: 'A', label: () => $t.projectModeA, desc: () => $t.projectModeADesc },
    { value: 'B', label: () => $t.projectModeB, desc: () => $t.projectModeBDesc },
    { value: 'C', label: () => $t.projectModeC, desc: () => $t.projectModeCDesc },
  ];

  const modeColors: Record<string, string> = {
    ALL: '#5c9e72',
    NONE: '#7a6e60',
    A: '#c45c26',
    B: '#d4a04a',
    C: '#d46a5c',
  };
</script>

<div class="view-container">
  <!-- View Header -->
  <div class="view-header">
    <div class="vh-left">
      <h2>{$t.projectMgmtTitle}</h2>
    </div>
    <div class="vh-right">
      {#if onClose}
        <button class="btn btn-ghost" onclick={onClose}>← {$t.settingsMenuReturn}</button>
      {/if}
      <button class="btn btn-primary" onclick={openCreateModal}>
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" style="width:13px;height:13px;margin-right:4px">
          <line x1="12" y1="5" x2="12" y2="19"></line>
          <line x1="5" y1="12" x2="19" y2="12"></line>
        </svg>
        {$t.projectRegister}
      </button>
    </div>
  </div>

  <div class="view-body">
    <div class="view-main">
      <div class="main-controls-row">
        <div class="search-input-wrap-clean search-projects-input">
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="icon-search-meta">
            <circle cx="11" cy="11" r="8"></circle>
            <line x1="21" y1="21" x2="16.65" y2="16.65"></line>
          </svg>
          <input
            class="search-input"
            placeholder={$t.projectSearchPlaceholder}
            bind:value={searchQuery}
          />
        </div>
      </div>

      {#if loading}
        <div class="loading-clean">{$t.loading}</div>
      {:else if error}
        <div class="empty-state-clean"><p>{error}</p></div>
      {:else if filteredProjects.length === 0}
        <div class="empty-state-clean">
          <p>{$t.projectNoProjects}</p>
        </div>
      {:else}
        <!-- Project Table -->
        <div class="table-container">
          <table class="projects-table">
            <thead>
              <tr>
                <th style="width: 30%">{$t.projectColName}</th>
                <th style="width: 38%">{$t.projectColKey}</th>
                <th style="width: 12%">{$t.projectColMode}</th>
                <th style="width: 12%">{$t.projectColDate}</th>
                <th style="width: 8%; text-align: center">{$t.projectColActions}</th>
              </tr>
            </thead>
            <tbody>
              {#each filteredProjects as project}
                <tr>
                  <td>
                    <div class="p-info-cell">
                      <span class="p-table-name">{project.name}</span>
                      {#if project.description}
                        <span class="p-table-desc">{project.description}</span>
                      {/if}
                    </div>
                  </td>
                  <td>
                    <!-- svelte-ignore a11y_click_events_have_key_events a11y_no_static_element_interactions -->
                    <div class="p-key-cell" onclick={() => copyKey(project.apiKey)} title={$t.projectCopy}>
                      <code class="p-table-key">{project.apiKey}</code>
                      <button type="button" class="btn-copy-icon" class:copied={copiedKey === project.apiKey}>
                        {copiedKey === project.apiKey ? $t.projectCopied : $t.projectCopy}
                      </button>
                    </div>
                  </td>
                  <td>
                    <span class="mode-badge" style="background: {modeColors[project.recordingMode] ?? '#64748b'}22; color: {modeColors[project.recordingMode] ?? '#64748b'}; border-color: {modeColors[project.recordingMode] ?? '#64748b'}44;">
                      {modeShortLabel(project.recordingMode)}
                    </span>
                  </td>
                  <td>
                    <span class="p-table-date">
                      {project.createdAt ? new Date(project.createdAt).toLocaleDateString() : '—'}
                    </span>
                  </td>
                  <td style="text-align: center">
                    <div class="action-cell">
                      <button class="btn-table-settings" onclick={() => openEditModal(project)} title={$t.projectSettings}>
                        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="icon-settings">
                          <circle cx="12" cy="12" r="3"></circle>
                          <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"></path>
                        </svg>
                      </button>
                      <button class="btn-table-delete" onclick={() => deleteProject(project.id)} title={$t.projectDelete}>
                        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="icon-trash">
                          <polyline points="3 6 5 6 21 6"></polyline>
                          <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path>
                        </svg>
                      </button>
                    </div>
                  </td>
                </tr>
              {/each}
            </tbody>
          </table>
        </div>
      {/if}
    </div>
  </div>
</div>

<!-- Unified Create/Edit Modal -->
{#if showModal}
  <!-- svelte-ignore a11y_click_events_have_key_events a11y_no_static_element_interactions -->
  <div class="modal-overlay" onclick={() => showModal = false}>
    <!-- svelte-ignore a11y_click_events_have_key_events a11y_no_static_element_interactions -->
    <div class="modal-panel" onclick={(e) => e.stopPropagation()}>
      <div class="modal-header">
        <div>
          <h3>{modalTitle}</h3>
          {#if isEditing}
            <p class="modal-sub">API Key: <code style="font-family:monospace;font-size:0.7rem">{projectApiKey}</code></p>
          {/if}
        </div>
        <button class="btn-modal-close" onclick={() => showModal = false}>
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" style="width:14px;height:14px">
            <line x1="18" y1="6" x2="6" y2="18"></line>
            <line x1="6" y1="6" x2="18" y2="18"></line>
          </svg>
        </button>
      </div>

      <div class="modal-body">
        <!-- Project Info Section -->
        <div class="modal-flex-row">
          <div class="form-group flex-1">
            <label for="m-name">{$t.projectNameLabel}</label>
            <input
              id="m-name"
              class="form-control"
              placeholder={$t.projectNamePlaceholder}
              bind:value={projectName}
              disabled={isEditing}
            />
          </div>
          <div class="form-group flex-2">
            <label for="m-desc">{$t.projectDescLabel}</label>
            <input
              id="m-desc"
              class="form-control"
              placeholder={$t.projectDescPlaceholder}
              bind:value={projectDesc}
              disabled={isEditing}
            />
          </div>
        </div>

        <div class="modal-divider"></div>

        <!-- Recording Mode Selection -->
        <div class="modal-section">
          <label class="modal-label">{$t.projectRecordingMode}</label>
          <div class="mode-options">
            {#each modeOptions as opt}
              <button
                class="mode-option"
                class:selected={recordingMode === opt.value}
                style={recordingMode === opt.value ? `border-color: ${modeColors[opt.value]}; background: ${modeColors[opt.value]}12;` : ''}
                onclick={() => {
                  recordingMode = opt.value;
                  if (opt.value === 'B' && isEditing) fetchActiveTerminals(projectApiKey);
                }}
              >
                <div class="mode-dot" style="background: {modeColors[opt.value]};"></div>
                <div class="mode-option-content">
                  <span class="mode-option-label">{opt.label()}</span>
                  <span class="mode-option-desc">{opt.desc()}</span>
                </div>
                {#if recordingMode === opt.value}
                  <div class="mode-check" style="color: {modeColors[opt.value]}">✓</div>
                {/if}
              </button>
            {/each}
          </div>
        </div>

        <!-- Mode A specific target list -->
        {#if recordingMode === 'A'}
          <div class="modal-section nested-section">
            <label class="modal-label" for="m-target">{$t.projectTargetUsers}</label>
            <textarea
              id="m-target"
              class="form-control"
              rows="2"
              placeholder={$t.projectTargetUsersPlaceholder}
              bind:value={targetUsers}
            ></textarea>
            <p class="field-hint">{$t.projectTargetUsersHint}</p>
          </div>
        {/if}

        <!-- Mode B specific active terminal controller -->
        {#if recordingMode === 'B'}
          <div class="modal-section nested-section">
            {#if !isEditing}
              <p class="field-hint">{$t.projectModeBCreateHint}</p>
            {:else}
              <div class="terminal-header">
                <label class="modal-label">{$t.projectModeB_terminals}</label>
                <button
                  class="btn-refresh"
                  onclick={() => fetchActiveTerminals(projectApiKey)}
                  disabled={loadingTerminals}
                >
                  <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="width:12px;height:12px">
                    <polyline points="23 4 23 10 17 10"></polyline>
                    <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"></path>
                  </svg>
                  {$t.projectModeB_refresh}
                </button>
              </div>

              {#if loadingTerminals}
                <div class="terminals-loading">{$t.loading}</div>
              {:else if activeTerminals.length === 0}
                <div class="terminals-empty">{$t.projectModeB_noTerminals}</div>
              {:else}
                <div class="terminals-list">
                  {#each activeTerminals as userId}
                    <div class="terminal-item">
                      <div class="terminal-user">
                        <div class="terminal-dot"></div>
                        <span class="terminal-id">{userId}</span>
                      </div>
                      <button
                        class="btn-trigger"
                        class:triggered={triggerResults[userId] === 'triggered'}
                        class:error={triggerResults[userId] === 'error'}
                        onclick={() => triggerRecording(userId)}
                        disabled={triggeringUser === userId || triggerResults[userId] === 'triggered'}
                      >
                        {#if triggerResults[userId] === 'triggering'}
                          {$t.projectModeB_triggering}
                        {:else if triggerResults[userId] === 'triggered'}
                          ✓ {$t.projectModeB_triggered}
                        {:else if triggerResults[userId] === 'error'}
                          ✕ {$t.projectModeB_triggerError}
                        {:else}
                          ● {$t.projectModeB_triggerBtn}
                        {/if}
                      </button>
                    </div>
                  {/each}
                </div>
              {/if}
            {/if}
          </div>
        {/if}

        {#if formError}
          <div class="form-error" style="margin-top:0.5rem">{formError}</div>
        {/if}
      </div>

      <div class="modal-footer">
        <button class="btn btn-ghost" onclick={() => showModal = false}>{$t.projectCancel}</button>
        <button class="btn btn-primary" onclick={handleSave} disabled={saving}>
          {saving ? $t.projectSavingSettings : $t.projectSaveSettings}
        </button>
      </div>
    </div>
  </div>
{/if}

<style>
  .view-container {
    display: flex;
    flex-direction: column;
    flex: 1;
    height: 100%;
    background: var(--bg);
    overflow: hidden;
  }

  .view-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 1.1rem 1.75rem;
    border-bottom: 1px solid var(--border);
    background: var(--panel);
  }

  .vh-left h2 {
    font-size: 1.05rem;
    font-weight: 700;
    margin: 0;
    color: var(--text);
    letter-spacing: -0.01em;
  }

  .vh-right {
    display: flex;
    align-items: center;
    gap: 0.5rem;
  }

  .view-body {
    display: flex;
    flex: 1;
    overflow: hidden;
  }

  .view-main {
    flex: 1;
    padding: 1.75rem;
    overflow-y: auto;
    display: flex;
    flex-direction: column;
    gap: 1.25rem;
    background: var(--bg);
  }

  .main-controls-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  .search-projects-input {
    width: 320px;
    padding: 0 0.65rem;
    background: var(--panel);
  }

  .icon-search-meta {
    width: 13px;
    height: 13px;
    color: var(--muted);
  }

  /* Table Style for Projects */
  .table-container {
    border: 1px solid var(--border);
    border-radius: 10px;
    background: var(--panel);
    overflow: hidden;
    box-shadow: 0 1px 0 rgba(255, 255, 255, 0.02) inset;
  }

  .projects-table {
    width: 100%;
    border-collapse: collapse;
    text-align: left;
    font-size: 0.8rem;
  }

  .projects-table th {
    background: rgba(0, 0, 0, 0.18);
    padding: 0.8rem 1rem;
    font-weight: 600;
    color: var(--muted);
    font-size: 0.7rem;
    text-transform: uppercase;
    letter-spacing: 0.04em;
    border-bottom: 1px solid var(--border);
  }

  .projects-table td {
    padding: 0.95rem 1rem;
    border-bottom: 1px solid var(--border);
    vertical-align: middle;
  }

  .projects-table tbody tr {
    transition: background 0.12s;
  }

  .projects-table tbody tr:hover {
    background: rgba(255, 255, 255, 0.02);
  }

  .projects-table tr:last-child td {
    border-bottom: none;
  }

  .p-info-cell {
    display: flex;
    flex-direction: column;
    gap: 0.2rem;
  }

  .p-table-name {
    font-weight: 700;
    color: var(--text);
  }

  .p-table-desc {
    font-size: 0.72rem;
    color: var(--muted);
    line-height: 1.35;
  }

  .p-key-cell {
    display: inline-flex;
    align-items: center;
    gap: 0.5rem;
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: 6px;
    padding: 0.35rem 0.6rem;
    cursor: pointer;
    max-width: 100%;
    transition: border-color 0.12s;
  }

  .p-key-cell:hover {
    border-color: var(--accent);
  }

  .p-table-key {
    font-family: monospace;
    font-size: 0.75rem;
    color: var(--text);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .btn-copy-icon {
    background: var(--panel);
    border: 1px solid var(--border);
    color: var(--muted);
    border-radius: 4px;
    font-size: 0.68rem;
    padding: 0.15rem 0.4rem;
    cursor: pointer;
    font-weight: 600;
    flex-shrink: 0;
    transition: all 0.12s;
  }

  .btn-copy-icon.copied {
    color: #10b981;
    border-color: rgba(16, 185, 129, 0.4);
    background: rgba(16, 185, 129, 0.1);
  }

  .p-key-cell:hover .btn-copy-icon:not(.copied) {
    color: var(--text);
    border-color: var(--accent-border);
  }

  .mode-badge {
    display: inline-block;
    font-size: 0.68rem;
    font-weight: 700;
    padding: 0.22rem 0.55rem;
    border-radius: 999px;
    border: 1px solid;
    letter-spacing: 0.02em;
  }

  .p-table-date {
    color: var(--muted);
    font-size: 0.75rem;
  }

  .action-cell {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 0.3rem;
  }

  .btn-table-settings {
    background: none;
    border: none;
    color: var(--muted);
    cursor: pointer;
    padding: 0.4rem;
    border-radius: 6px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    transition: all 0.12s;
  }

  .btn-table-settings:hover {
    color: var(--accent);
    background: rgba(99, 102, 241, 0.08);
  }

  .icon-settings {
    width: 13px;
    height: 13px;
  }

  .btn-table-delete {
    background: none;
    border: none;
    color: var(--muted);
    cursor: pointer;
    padding: 0.4rem;
    border-radius: 6px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    transition: all 0.12s;
  }

  .btn-table-delete:hover {
    color: var(--danger);
    background: rgba(239, 68, 68, 0.08);
  }

  .icon-trash {
    width: 13px;
    height: 13px;
  }

  /* ── Settings Modal ── */
  .modal-overlay {
    position: fixed;
    inset: 0;
    background: rgba(0, 0, 0, 0.55);
    display: flex;
    align-items: center;
    justify-content: center;
    z-index: 1000;
    backdrop-filter: blur(8px);
    -webkit-backdrop-filter: blur(8px);
    animation: fadeIn 0.15s ease;
  }

  @keyframes fadeIn {
    from { opacity: 0; }
    to   { opacity: 1; }
  }

  .modal-panel {
    background: var(--panel);
    border: 1px solid var(--border);
    border-radius: 14px;
    width: 600px;
    max-width: 95vw;
    max-height: 90vh;
    display: flex;
    flex-direction: column;
    box-shadow: 0 24px 64px rgba(0, 0, 0, 0.55);
    animation: slideUp 0.18s ease;
  }

  @keyframes slideUp {
    from { transform: translateY(16px); opacity: 0; }
    to   { transform: translateY(0);    opacity: 1; }
  }

  .modal-header {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    padding: 1.25rem 1.5rem 1rem;
    border-bottom: 1px solid var(--border);
  }

  .modal-header h3 {
    margin: 0 0 0.2rem;
    font-size: 0.95rem;
    font-weight: 700;
    color: var(--text);
  }

  .modal-sub {
    margin: 0;
    font-size: 0.72rem;
    color: var(--muted);
  }

  .btn-modal-close {
    background: var(--surface);
    border: 1px solid var(--border);
    color: var(--muted);
    cursor: pointer;
    width: 26px;
    height: 26px;
    border-radius: 6px;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
    transition: all 0.12s;
  }

  .btn-modal-close:hover {
    color: var(--text);
    border-color: var(--accent);
  }

  .modal-body {
    padding: 1.25rem 1.5rem;
    overflow-y: auto;
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: 1rem;
  }

  .modal-flex-row {
    display: flex;
    gap: 0.75rem;
  }

  .flex-1 { flex: 1; }
  .flex-2 { flex: 2; }

  .modal-divider {
    height: 1px;
    background: var(--border);
    margin: 0.5rem 0;
  }

  .modal-section {
    display: flex;
    flex-direction: column;
    gap: 0.6rem;
  }

  .nested-section {
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: 8px;
    padding: 0.75rem 1rem;
    margin-top: -0.25rem;
  }

  .modal-label {
    font-size: 0.72rem;
    font-weight: 700;
    color: var(--muted);
    text-transform: uppercase;
    letter-spacing: 0.04em;
  }

  .form-group {
    display: flex;
    flex-direction: column;
    gap: 0.4rem;
  }

  .form-group label {
    font-size: 0.72rem;
    font-weight: 600;
    color: var(--muted);
    text-transform: uppercase;
  }

  .form-control {
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: 6px;
    padding: 0.5rem 0.75rem;
    color: var(--text);
    font-size: 0.8rem;
    outline: none;
    transition: border-color 0.15s;
    font-family: inherit;
    resize: vertical;
  }

  .form-control:focus {
    border-color: var(--accent);
  }

  .form-control:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }

  .mode-options {
    display: flex;
    flex-direction: column;
    gap: 0.4rem;
  }

  .mode-option {
    display: flex;
    align-items: center;
    gap: 0.75rem;
    padding: 0.6rem 0.85rem;
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: 8px;
    cursor: pointer;
    text-align: left;
    transition: border-color 0.15s, background 0.15s;
  }

  .mode-dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    flex-shrink: 0;
  }

  .mode-option-content {
    display: flex;
    flex-direction: column;
    gap: 0.15rem;
    flex: 1;
  }

  .mode-option-label {
    font-size: 0.8rem;
    font-weight: 600;
    color: var(--text);
  }

  .mode-option-desc {
    font-size: 0.7rem;
    color: var(--muted);
  }

  .mode-check {
    font-size: 0.85rem;
    font-weight: 700;
    flex-shrink: 0;
  }

  .field-hint {
    margin: 0;
    font-size: 0.7rem;
    color: var(--muted);
    line-height: 1.4;
  }

  /* Terminal List (Mode B) */
  .terminal-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  .btn-refresh {
    display: inline-flex;
    align-items: center;
    gap: 0.35rem;
    padding: 0.3rem 0.6rem;
    font-size: 0.7rem;
    font-weight: 600;
    color: var(--muted);
    background: var(--panel);
    border: 1px solid var(--border);
    border-radius: 5px;
    cursor: pointer;
    transition: all 0.12s;
  }

  .btn-refresh:hover { color: var(--text); border-color: var(--accent); }
  .btn-refresh:disabled { opacity: 0.5; cursor: not-allowed; }

  .terminals-loading,
  .terminals-empty {
    font-size: 0.78rem;
    color: var(--muted);
    padding: 0.75rem;
    background: var(--panel);
    border: 1px solid var(--border);
    border-radius: 6px;
    text-align: center;
  }

  .terminals-list {
    display: flex;
    flex-direction: column;
    gap: 0.4rem;
  }

  .terminal-item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 0.6rem 0.9rem;
    background: var(--panel);
    border: 1px solid var(--border);
    border-radius: 6px;
  }

  .terminal-user {
    display: flex;
    align-items: center;
    gap: 0.5rem;
  }

  .terminal-dot {
    width: 7px;
    height: 7px;
    border-radius: 50%;
    background: #10b981;
    box-shadow: 0 0 6px #10b98177;
    animation: pulse-dot 1.5s ease-in-out infinite;
  }

  @keyframes pulse-dot {
    0%, 100% { opacity: 1; }
    50%       { opacity: 0.4; }
  }

  .terminal-id {
    font-size: 0.8rem;
    font-weight: 600;
    color: var(--text);
    font-family: monospace;
  }

  .btn-trigger {
    padding: 0.3rem 0.75rem;
    font-size: 0.72rem;
    font-weight: 700;
    background: rgba(245, 158, 11, 0.12);
    color: #f59e0b;
    border: 1px solid rgba(245, 158, 11, 0.3);
    border-radius: 5px;
    cursor: pointer;
    transition: all 0.12s;
    white-space: nowrap;
  }

  .btn-trigger:hover:not(:disabled) {
    background: rgba(245, 158, 11, 0.22);
    border-color: #f59e0b;
  }

  .btn-trigger.triggered {
    background: rgba(16, 185, 129, 0.12);
    color: #10b981;
    border-color: rgba(16, 185, 129, 0.3);
  }

  .btn-trigger.error {
    background: rgba(239, 68, 68, 0.12);
    color: var(--danger);
    border-color: rgba(239, 68, 68, 0.3);
  }

  .btn-trigger:disabled {
    cursor: not-allowed;
    opacity: 0.7;
  }

  /* Form/Modal feedbacks */
  .form-error {
    font-size: 0.76rem;
    color: var(--danger);
    background: rgba(239, 68, 68, 0.08);
    border: 1px solid rgba(239, 68, 68, 0.15);
    border-radius: 6px;
    padding: 0.55rem 0.75rem;
    font-weight: 600;
  }

  /* Modal Footer */
  .modal-footer {
    display: flex;
    align-items: center;
    justify-content: flex-end;
    gap: 0.6rem;
    padding: 1rem 1.5rem;
    border-top: 1px solid var(--border);
    background: var(--panel);
    border-radius: 0 0 14px 14px;
  }

  :global(.view-container .btn-primary) {
    background: var(--accent);
    border-color: var(--accent);
    color: #fff;
  }

  :global(.view-container .btn-primary:hover:not(:disabled)) {
    filter: brightness(1.08);
  }
</style>
