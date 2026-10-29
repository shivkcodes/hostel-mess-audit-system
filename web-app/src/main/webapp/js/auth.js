"use strict";

document.addEventListener("DOMContentLoaded", () => {
    const publicMenu = document.querySelector(".public-menu");
    const mobileMenu = window.matchMedia("(max-width: 720px)");
    if (publicMenu instanceof HTMLDetailsElement) {
        const syncPublicMenu = () => { publicMenu.open = !mobileMenu.matches; };
        syncPublicMenu();
        if (typeof mobileMenu.addEventListener === "function") {
            mobileMenu.addEventListener("change", syncPublicMenu);
        }
        publicMenu.querySelectorAll(".public-nav a").forEach((link) => {
            link.addEventListener("click", () => {
                if (mobileMenu.matches) publicMenu.open = false;
            });
        });
    }

    document.querySelectorAll("[data-password-toggle]").forEach((button) => {
        button.addEventListener("click", () => {
            const input = document.getElementById(button.dataset.passwordToggle);
            if (!(input instanceof HTMLInputElement)) return;
            const reveal = input.type === "password";
            input.type = reveal ? "text" : "password";
            button.textContent = reveal ? "Hide" : "Show";
            button.setAttribute("aria-pressed", String(reveal));
            input.focus();
        });
    });

    document.querySelectorAll("form.auth-form").forEach((form) => {
        form.addEventListener("submit", () => {
            if (!form.reportValidity()) return;
            const button = form.querySelector("[data-submit-button]");
            if (button instanceof HTMLButtonElement) {
                button.disabled = true;
                button.textContent = "Please wait…";
                button.setAttribute("aria-busy", "true");
            }
        });
    });
});
