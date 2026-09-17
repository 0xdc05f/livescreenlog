<script lang="ts">
  import { onMount } from 'svelte';
  import { t } from '../i18n';

  let { onClose }: { onClose?: () => void } = $props();

  let users = $state<any[]>([]);
  let loading = $state(false);
  let error = $state('');
  let success = $state('');

  // create form
  let username = $state('');
  let password = $state('');
  let email = $state('');
  let role = $state<'SUPER_ADMIN' | 'ADMIN' | 'VIEWER'>('ADMIN');
  let creating = $state(false);
  let formError = $state('');

  // project assignment (super-admin)
  let selectedUser = $state<any>(null);
  let projects = $state<any[]>([]);
  let userAssignments = $state<any[]>([]);
  let assignProjectId = $state<number | null>(null);
  let assignRole = $state<'OWNER' | 'ADMIN' | 'VIEWER'>('VIEWER');
  let assignError = $state('');
  let assignLoading = $state(false);
  let resetPassword = $state('');
  let resetting = $state(false);

  async function loadUsers() {
    loading = true;
    error = '';
    try {
      const res = await fetch('/api/admin/users');
      if (res.ok) {
        users = await res.json();
      } else {
        error = $t.usersLoadError;
      }
    } catch {
      error = $t.usersLoadError;
    } finally {
      loading = false;
    }
  }

  async function createUser() {
    if (!username.trim() || !password.trim()) {
      formError = $t.usersUsernamePasswordRequired;
      return;
    }
    if (password.length < 8) {
      formError = $t.usersPasswordTooShort;
      return;
    }
    creating = true;
    formError = '';
    error = '';
    success = '';
    try {
      const res = await fetch('/api/admin/users', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: username.trim(), password, email: email.trim() || null, role })
      });
      if (res.ok) {
        const created = await res.json();
        users = [...users, created];
        username = '';
        password = '';
        email = '';
        role = 'ADMIN';
        success = $t.usersCreated;
        setTimeout(() => success = '', 1800);
      } else {
        const body = await res.json().catch(() => ({}));
        formError = body?.error || $t.usersCreateError;
      }
    } catch {
      formError = $t.usersCreateError;
    } finally {
      creating = false;
    }
  }

  async function toggleEnabled(user: any) {
    error = '';
    success = '';
    const newEnabled = !user.enabled;
    try {
      const res = await fetch(`/api/admin/users/${user.id}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ enabled: newEnabled })
      });
      if (res.ok) {
        const updated = await res.json();
        users = users.map(u => u.id === updated.id ? updated : u);
        success = newEnabled ? $t.usersEnabledMsg : $t.usersDisabledMsg;
        setTimeout(() => success = '', 1400);
      } else {
        const body = await res.json().catch(() => ({}));
        if (body?.error?.includes('last enabled')) {
          error = $t.usersLastSuperError;
        } else {
          error = body?.error || $t.usersUpdateError;
        }
      }
    } catch {
      error = $t.usersUpdateError;
    }
  }

  async function changeRole(user: any, newRole: string) {
    error = '';
    success = '';
    try {
      const res = await fetch(`/api/admin/users/${user.id}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ role: newRole })
      });
      if (res.ok) {
        const updated = await res.json();
        users = users.map(u => u.id === updated.id ? updated : u);
        success = $t.usersRoleUpdated;
        setTimeout(() => success = '', 1400);
      } else {
        const body = await res.json().catch(() => ({}));
        error = body?.error || $t.usersUpdateError;
      }
    } catch {
      error = $t.usersUpdateError;
    }
  }

  async function loadProjects() {
    try {
      const res = await fetch('/api/projects');
      if (res.ok) {
        projects = await res.json();
      } else {
        error = $t.usersLoadProjectsError;
      }
    } catch {
      error = $t.usersLoadProjectsError;
    }
  }

  async function loadUserAssignments(userId: number) {
    assignError = '';
    try {
      const res = await fetch(`/api/admin/users/${userId}/projects`);
      if (res.ok) {
        userAssignments = await res.json();
      } else {
        error = $t.usersLoadAssignmentsError;
      }
    } catch {
      error = $t.usersLoadAssignmentsError;
    }
  }

  function selectUserForAssign(user: any) {
    selectedUser = user;
    userAssignments = [];
    assignProjectId = null;
    assignRole = 'VIEWER';
    assignError = '';
    resetPassword = '';
    loadProjects();
    loadUserAssignments(user.id);
  }

  async function resetUserPassword() {
    if (!selectedUser) return;
    if (!resetPassword || resetPassword.length < 8) {
      assignError = $t.accountTooShort;
      return;
    }
    resetting = true;
    assignError = '';
    error = '';
    success = '';
    try {
      const res = await fetch(`/api/admin/users/${selectedUser.id}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ newPassword: resetPassword })
      });
      if (res.ok) {
        resetPassword = '';
        success = $t.usersPasswordReset;
        setTimeout(() => success = '', 1400);
      } else {
        const body = await res.json().catch(() => ({}));
        assignError = body?.error || $t.usersUpdateError;
      }
    } catch {
      assignError = $t.usersUpdateError;
    } finally {
      resetting = false;
    }
  }

  function clearSelected() {
    selectedUser = null;
    userAssignments = [];
    assignError = '';
  }

  async function assignToProject() {
    if (!selectedUser || !assignProjectId) {
      assignError = $t.usersSelectProjectError;
      return;
    }
    assignLoading = true;
    assignError = '';
    error = '';
    success = '';
    try {
      const res = await fetch(`/api/admin/users/${selectedUser.id}/projects`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ projectId: assignProjectId, roleInProject: assignRole })
      });
      if (res.ok) {
        const assigned = await res.json();
        userAssignments = [...userAssignments, assigned];
        success = $t.usersProjectAssigned;
        setTimeout(() => success = '', 1400);
        assignProjectId = null;
      } else {
        const body = await res.json().catch(() => ({}));
        if (body?.error?.includes('already')) {
          assignError = $t.usersAlreadyAssigned;
        } else {
          assignError = body?.error || $t.usersAssignError;
        }
      }
    } catch {
      assignError = $t.usersAssignError;
    } finally {
      assignLoading = false;
    }
  }

  async function removeAssignment(projectId: number) {
    if (!selectedUser) return;
    assignError = '';
    error = '';
    success = '';
    try {
      const res = await fetch(`/api/admin/users/${selectedUser.id}/projects/${projectId}`, {
        method: 'DELETE'
      });
      if (res.ok || res.status === 204) {
        userAssignments = userAssignments.filter((a: any) => a.projectId !== projectId);
        success = $t.usersAssignmentRemoved;
        setTimeout(() => success = '', 1400);
      } else {
        const body = await res.json().catch(() => ({}));
        assignError = body?.error || $t.usersRemoveError;
      }
    } catch {
      assignError = $t.usersRemoveError;
    }
  }

  onMount(() => {
    loadUsers();
  });
</script>

<div class="view-container">
  <div class="view-header">
    <div class="vh-left">
      <h2>{$t.usersTitle}</h2>
    </div>
    <div class="vh-right">
      <button class="btn btn-ghost" onclick={onClose}>← {$t.settingsMenuReturn}</button>
    </div>
  </div>

  <div class="view-body">
    <div class="view-main users-main">
      {#if loading}
        <div class="hint">{$t.usersLoading}</div>
      {:else}
        {#if error}
          <div class="alert error">{error}</div>
        {/if}
        {#if success}
          <div class="alert success">{success}</div>
        {/if}

        <div class="config-card">
          <h3>{$t.usersFormTitle}</h3>
          {#if formError}
            <div class="alert error small">{formError}</div>
          {/if}
          <div class="user-form">
            <div class="row">
              <label class="flabel" for="user-username">{$t.usersUsernameLabel}</label>
              <input id="user-username" type="text" bind:value={username} class="cfg-input" placeholder="adminuser" />
            </div>
            <div class="row">
              <label class="flabel" for="user-password">{$t.usersPasswordLabel}</label>
              <input id="user-password" type="password" bind:value={password} class="cfg-input" placeholder="min 8 chars" />
            </div>
            <div class="row">
              <label class="flabel" for="user-email">{$t.usersEmailLabel}</label>
              <input id="user-email" type="email" bind:value={email} class="cfg-input" placeholder="optional@example.com" />
            </div>
            <div class="row">
              <label class="flabel" for="user-role">{$t.usersRoleLabel}</label>
              <select id="user-role" bind:value={role} class="cfg-input">
                <option value="SUPER_ADMIN">{$t.usersRoleSuper}</option>
                <option value="ADMIN">{$t.usersRoleAdmin}</option>
                <option value="VIEWER">{$t.usersRoleViewer}</option>
              </select>
              <button class="btn btn-primary" disabled={creating} onclick={createUser}>
                {creating ? $t.usersCreating : $t.usersCreate}
              </button>
            </div>
          </div>
        </div>

        <div class="table-container">
          <table class="projects-table users-table">
            <thead>
              <tr>
                <th style="width: 22%">{$t.usersColUsername}</th>
                <th style="width: 22%">{$t.usersColEmail}</th>
                <th style="width: 18%">{$t.usersColRole}</th>
                <th style="width: 12%">{$t.usersColEnabled}</th>
                <th style="width: 14%">{$t.usersColCreated}</th>
                <th style="width: 12%; text-align: center">{$t.usersColActions}</th>
              </tr>
            </thead>
            <tbody>
              {#if users.length === 0}
                <tr>
                  <td colspan="6" class="empty">{$t.usersNoUsers}</td>
                </tr>
              {:else}
                {#each users as user (user.id)}
                  <tr>
                    <td><span class="u-name">{user.username}</span></td>
                    <td><span class="u-email">{user.email || ''}</span></td>
                    <td>
                      <select class="role-select" value={user.role} onchange={(e) => changeRole(user, (e.target as HTMLSelectElement).value)}>
                        <option value="SUPER_ADMIN">{$t.usersRoleSuper}</option>
                        <option value="ADMIN">{$t.usersRoleAdmin}</option>
                        <option value="VIEWER">{$t.usersRoleViewer}</option>
                      </select>
                    </td>
                    <td>
                      <span class={user.enabled ? 'status on' : 'status off'}>{user.enabled ? 'ON' : 'OFF'}</span>
                    </td>
                    <td>
                      <span class="u-date">{user.createdAt ? new Date(user.createdAt).toLocaleDateString() : ''}</span>
                    </td>
                    <td style="text-align: center">
                      <button
                        class="btn btn-ghost btn-small"
                        onclick={() => toggleEnabled(user)}
                        title={user.enabled ? $t.usersDisable : $t.usersEnable}
                      >
                        {user.enabled ? $t.usersDisable : $t.usersEnable}
                      </button>
                      <button
                        class="btn btn-ghost btn-small"
                        style="margin-left: 0.25rem"
                        onclick={() => selectUserForAssign(user)}
                        title={$t.usersAssignProjects}
                      >
                        {$t.usersAssignProjects}
                      </button>
                    </td>
                  </tr>
                {/each}
              {/if}
            </tbody>
          </table>
        </div>

        {#if selectedUser}
          <div class="config-card assign-card">
            <div class="assign-header">
              <h3>{$t.usersAssignProjects} — {selectedUser.username}</h3>
              <button class="btn btn-ghost btn-small" onclick={clearSelected}>×</button>
            </div>
            {#if assignError}
              <div class="alert error small">{assignError}</div>
            {/if}
            <div class="assigned-list">
              <div class="subhead">{$t.usersAssignedProjects}</div>
              {#if userAssignments.length === 0}
                <div class="hint">{$t.usersNoAssignments}</div>
              {:else}
                <table class="mini-table">
                  <thead>
                    <tr>
                      <th>{$t.usersProject}</th>
                      <th>{$t.usersRoleInProject}</th>
                      <th></th>
                    </tr>
                  </thead>
                  <tbody>
                    {#each userAssignments as a (a.projectId)}
                      <tr>
                        <td>{a.projectName || a.projectId}</td>
                        <td><span class="role-badge">{a.roleInProject}</span></td>
                        <td>
                          <button class="btn btn-ghost btn-small" onclick={() => removeAssignment(a.projectId)}>{$t.usersRemove}</button>
                        </td>
                      </tr>
                    {/each}
                  </tbody>
                </table>
              {/if}
            </div>
            <div class="assign-form">
              <div class="row">
                <label class="flabel" for="reset-password">{$t.usersResetPassword}</label>
                <input id="reset-password" type="password" bind:value={resetPassword} class="cfg-input" placeholder="min 8 chars" />
                <button class="btn btn-primary" disabled={resetting || !resetPassword} onclick={resetUserPassword}>
                  {$t.usersResetPassword}
                </button>
              </div>
              <div class="row">
                <label class="flabel" for="assign-project">{$t.usersProject}</label>
                <select id="assign-project" bind:value={assignProjectId} class="cfg-input">
                  <option value={null}>{$t.usersSelectPlaceholder}</option>
                  {#each projects as p (p.id)}
                    <option value={p.id}>{p.name} (#{p.id})</option>
                  {/each}
                </select>
              </div>
              <div class="row">
                <label class="flabel" for="assign-role">{$t.usersRoleInProject}</label>
                <select id="assign-role" bind:value={assignRole} class="cfg-input role-select">
                  <option value="OWNER">OWNER</option>
                  <option value="ADMIN">ADMIN</option>
                  <option value="VIEWER">VIEWER</option>
                </select>
                <button class="btn btn-primary" disabled={assignLoading || !assignProjectId} onclick={assignToProject}>
                  {assignLoading ? '…' : $t.usersAssign}
                </button>
              </div>
            </div>
          </div>
        {/if}
      {/if}
    </div>
  </div>
</div>

<style>
  .users-main {
    max-width: 980px;
  }
  .config-card {
    background: var(--panel);
    border: 1px solid var(--border);
    border-radius: 10px;
    padding: 1.1rem 1.25rem;
    margin-bottom: 1rem;
  }
  .config-card h3 {
    margin: 0 0 0.6rem;
    font-size: 0.95rem;
    font-weight: 700;
  }
  .user-form {
    display: flex;
    flex-direction: column;
    gap: 0.5rem;
  }
  .row {
    display: flex;
    gap: 0.6rem;
    align-items: center;
  }
  .flabel {
    width: 110px;
    font-size: 0.75rem;
    color: var(--muted);
    flex-shrink: 0;
  }
  .cfg-input {
    flex: 1;
    padding: 0.45rem 0.6rem;
    border: 1px solid var(--border);
    border-radius: 6px;
    font-size: 0.85rem;
    background: #fff;
  }
  .alert {
    padding: 0.5rem 0.75rem;
    border-radius: 6px;
    font-size: 0.8rem;
    margin-bottom: 0.75rem;
  }
  .alert.error {
    background: #fef2f2;
    border: 1px solid #fecaca;
    color: #991b1b;
  }
  .alert.success {
    background: #ecfdf5;
    border: 1px solid #a7f3d0;
    color: #065f46;
  }
  .alert.small {
    margin: 0 0 0.5rem;
    padding: 0.35rem 0.5rem;
    font-size: 0.75rem;
  }
  .hint {
    color: var(--muted);
  }
  .table-container {
    border: 1px solid var(--border);
    border-radius: 10px;
    background: var(--panel);
    overflow: hidden;
  }
  .users-table {
    width: 100%;
    border-collapse: collapse;
    font-size: 0.8rem;
  }
  .users-table th {
    background: rgba(0, 0, 0, 0.18);
    padding: 0.7rem 0.9rem;
    font-weight: 600;
    color: var(--muted);
    font-size: 0.68rem;
    text-transform: uppercase;
    letter-spacing: 0.04em;
    border-bottom: 1px solid var(--border);
  }
  .users-table td {
    padding: 0.7rem 0.9rem;
    border-bottom: 1px solid var(--border);
    vertical-align: middle;
  }
  .users-table tr:last-child td {
    border-bottom: none;
  }
  .u-name {
    font-weight: 600;
    color: var(--text);
  }
  .u-email {
    font-size: 0.75rem;
    color: var(--muted);
  }
  .role-select {
    font-size: 0.78rem;
    padding: 0.25rem 0.4rem;
    border: 1px solid var(--border);
    border-radius: 4px;
    background: var(--surface);
    color: var(--text);
  }
  .status {
    font-size: 0.7rem;
    padding: 0.1rem 0.4rem;
    border-radius: 3px;
    font-weight: 600;
  }
  .status.on { background: #d1fae5; color: #065f46; }
  .status.off { background: #fee2e2; color: #991b1b; }
  .u-date {
    font-size: 0.72rem;
    color: var(--muted-2);
  }
  .btn-small {
    height: 26px;
    font-size: 0.72rem;
    padding: 0 0.5rem;
  }
  .empty {
    color: var(--muted);
    font-style: italic;
    padding: 1rem;
  }
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
  }
  .view-body {
    display: flex;
    flex: 1;
    overflow: hidden;
  }
  .view-main {
    flex: 1;
    padding: 1.5rem 1.75rem;
    overflow-y: auto;
    display: flex;
    flex-direction: column;
    gap: 1rem;
  }
  .assign-card {
    margin-top: 0.5rem;
  }
  .assign-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 0.5rem;
  }
  .assign-header h3 {
    margin: 0;
    font-size: 0.9rem;
  }
  .subhead {
    font-size: 0.75rem;
    color: var(--muted);
    margin: 0.25rem 0;
  }
  .assigned-list {
    margin-bottom: 0.5rem;
  }
  .mini-table {
    width: 100%;
    border-collapse: collapse;
    font-size: 0.75rem;
  }
  .mini-table th, .mini-table td {
    padding: 0.25rem 0.4rem;
    border-bottom: 1px solid var(--border);
    text-align: left;
  }
  .mini-table th {
    font-weight: 600;
    color: var(--muted);
    font-size: 0.65rem;
    text-transform: uppercase;
  }
  .role-badge {
    font-size: 0.65rem;
    padding: 0.05rem 0.35rem;
    border: 1px solid var(--border);
    border-radius: 3px;
    background: var(--surface);
  }
  .assign-form {
    margin-top: 0.4rem;
    padding-top: 0.4rem;
    border-top: 1px dashed var(--border);
  }
</style>
