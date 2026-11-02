// Si ya hay sesión iniciada, vamos directamente al inicio
document.addEventListener('DOMContentLoaded', async () => {
    try {
        await api('/api/auth/me', { sinRedirigir: true });
        window.location.href = 'dashboard.html';
        return;
    } catch (e) { /* no hay sesión: mostramos el formulario */ }

    document.getElementById('formLogin').addEventListener('submit', async (evento) => {
        evento.preventDefault();
        const form = {
            username: document.getElementById('username').value.trim(),
            password: document.getElementById('password').value
        };
        try {
            await api('/api/auth/login', { method: 'POST', form: form, sinRedirigir: true });
            window.location.href = 'dashboard.html';
        } catch (e) {
            mostrarMensaje(e.message, 'error');
        }
    });
});
