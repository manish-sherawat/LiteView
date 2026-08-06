/**
 * LiteView Official Website Logic
 */

document.addEventListener('DOMContentLoaded', () => {
    initFaqAccordion();
});

/* --------------------------------------------------
 * FAQ Accordion Toggle
 * -------------------------------------------------- */
function initFaqAccordion() {
    const faqQuestions = document.querySelectorAll('.faq-question');

    faqQuestions.forEach(q => {
        q.addEventListener('click', () => {
            const item = q.parentElement;
            const isOpen = item.classList.contains('active');

            document.querySelectorAll('.faq-item').forEach(i => i.classList.remove('active'));

            if (!isOpen) {
                item.classList.add('active');
            }
        });
    });
}
