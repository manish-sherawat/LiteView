/**
 * LiteView Official Website Logic — Precision Architecture with Smooth Animations & Vanta.js Fog
 */

document.addEventListener('DOMContentLoaded', () => {
    initVantaFogBackground();
    initNavbarScroll();
    initScrollReveal();
    initCardTiltEffect();
    initFaqAccordion();
    initSmoothScroll();
});

/* --------------------------------------------------
 * Vanta.js Animated Fog Background (Tailored for Light Theme)
 * -------------------------------------------------- */
let vantaEffect = null;

function initVantaFogBackground() {
    const bgElement = document.getElementById('vanta-bg');
    if (!bgElement || typeof VANTA === 'undefined' || typeof THREE === 'undefined') {
        return;
    }

    try {
        vantaEffect = VANTA.FOG({
            el: "#vanta-bg",
            mouseControls: true,
            touchControls: true,
            gyroControls: false,
            minHeight: 200.00,
            minWidth: 200.00,
            highlightColor: 0x93c5fd, // Vivid cool M-blue tone for high visibility
            midtoneColor: 0xc7d2fe,   // Crisp soft indigo/slate tone
            lowlightColor: 0xe0e7ff,  // Contrasting ambient light cloud
            baseColor: 0xf8fafc,      // Clean soft canvas foundation
            blurFactor: 0.55,         // Sharper defined fog billows
            speed: 1.60,              // Dynamic, responsive fluid motion
            zoom: 1.00
        });
    } catch (err) {
        console.warn('Vanta.js failed to initialize:', err);
    }
}

// Clean up effect on unload if needed
window.addEventListener('beforeunload', () => {
    if (vantaEffect) {
        vantaEffect.destroy();
    }
});

/* --------------------------------------------------
 * Navbar Scroll Transition
 * -------------------------------------------------- */
function initNavbarScroll() {
    const nav = document.getElementById('top-nav');
    if (!nav) return;

    window.addEventListener('scroll', () => {
        if (window.scrollY > 30) {
            nav.classList.add('scrolled');
        } else {
            nav.classList.remove('scrolled');
        }
    }, { passive: true });
}

/* --------------------------------------------------
 * Scroll-Driven Intersection Observer Reveal
 * -------------------------------------------------- */
function initScrollReveal() {
    const revealElements = document.querySelectorAll('.reveal');
    if (!revealElements.length) return;

    if (!('IntersectionObserver' in window)) {
        revealElements.forEach(el => el.classList.add('is-visible'));
        return;
    }

    const observer = new IntersectionObserver((entries) => {
        entries.forEach(entry => {
            if (entry.isIntersecting) {
                entry.target.classList.add('is-visible');
                observer.unobserve(entry.target);
            }
        });
    }, {
        threshold: 0.12,
        rootMargin: '0px 0px -40px 0px'
    });

    revealElements.forEach(el => observer.observe(el));
}

/* --------------------------------------------------
 * Interactive Card Micro-Tilt Physics
 * -------------------------------------------------- */
function initCardTiltEffect() {
    const cards = document.querySelectorAll('.feature-photo-card, .format-box, .hero-device');

    cards.forEach(card => {
        card.addEventListener('mousemove', (e) => {
            const rect = card.getBoundingClientRect();
            const x = e.clientX - rect.left;
            const y = e.clientY - rect.top;
            
            const centerX = rect.width / 2;
            const centerY = rect.height / 2;
            
            const rotateX = ((y - centerY) / centerY) * -3;
            const rotateY = ((x - centerX) / centerX) * 3;

            card.style.transform = `perspective(1000px) rotateX(${rotateX}deg) rotateY(${rotateY}deg) translateY(-4px)`;
        });

        card.addEventListener('mouseleave', () => {
            card.style.transform = '';
        });
    });
}

/* --------------------------------------------------
 * Technical FAQ Accordion Toggle
 * -------------------------------------------------- */
function initFaqAccordion() {
    const faqButtons = document.querySelectorAll('.faq-question-btn');

    faqButtons.forEach(btn => {
        btn.addEventListener('click', () => {
            const currentItem = btn.closest('.faq-item');
            const isActive = currentItem.classList.contains('active');

            // Close all items
            document.querySelectorAll('.faq-item').forEach(item => {
                item.classList.remove('active');
            });

            // Toggle selected item
            if (!isActive) {
                currentItem.classList.add('active');
            }
        });
    });
}

/* --------------------------------------------------
 * Smooth Anchor Navigation
 * -------------------------------------------------- */
function initSmoothScroll() {
    const navLinks = document.querySelectorAll('a[href^="#"]');

    navLinks.forEach(link => {
        link.addEventListener('click', function (e) {
            const targetId = this.getAttribute('href');
            if (targetId === '#') return;

            const targetEl = document.querySelector(targetId);
            if (targetEl) {
                e.preventDefault();
                const navOffset = 64;
                const elementPosition = targetEl.getBoundingClientRect().top;
                const offsetPosition = elementPosition + window.pageYOffset - navOffset;

                window.scrollTo({
                    top: offsetPosition,
                    behavior: 'smooth'
                });
            }
        });
    });
}
