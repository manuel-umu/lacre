// Buscador de la vista general: filtra las filas por NIF o nombre y se reaplica cuando htmx refresca la tabla.
const buscar = document.getElementById('buscar');
const filtrar = () => {
    const texto = buscar.value.trim().toLowerCase();
    document.querySelectorAll('#tabla tbody tr')
        .forEach(fila => fila.hidden = texto !== '' && !fila.dataset.busqueda.includes(texto));
};
buscar.addEventListener('input', filtrar);
document.body.addEventListener('htmx:load', filtrar);
